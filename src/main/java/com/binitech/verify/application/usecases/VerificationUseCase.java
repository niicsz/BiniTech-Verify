package com.binitech.verify.application.usecases;

import com.binitech.verify.application.ports.inbound.VerificationUseCasePort;
import com.binitech.verify.application.ports.outbound.CodeGeneratorPort;
import com.binitech.verify.application.ports.outbound.CodeHasherPort;
import com.binitech.verify.application.ports.outbound.CodeSenderPort;
import com.binitech.verify.application.ports.outbound.VerificationRepositoryPort;
import com.binitech.verify.application.ports.outbound.WhatsAppPort;
import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Verification;
import com.binitech.verify.domain.exception.BusinessException;
import com.binitech.verify.domain.exception.ChannelUnavailableException;
import com.binitech.verify.domain.exception.InvalidCodeException;
import com.binitech.verify.domain.exception.ResourceNotFoundException;
import com.binitech.verify.domain.exception.TooManyRequestsException;
import com.binitech.verify.domain.exception.VerificationExpiredException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerificationUseCase implements VerificationUseCasePort {

  private static final Pattern CODE = Pattern.compile("\\d{6}");
  private static final Pattern CODE_IN_MESSAGE = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
  private static final Duration DESTINATION_WINDOW = Duration.ofHours(1);
  private static final String WHATSAPP_SCOPE = "whatsapp";
  private static final String WHATSAPP_HELP =
      "Olá! Este número só confirma cadastros da BiniTech. Para confirmar o seu, toque no botão"
          + " do WhatsApp na tela de cadastro e envie a mensagem sem alterar o código.";
  private static final String WHATSAPP_NOT_FOUND =
      "Não encontrei esse código. Ele pode ter vencido ou já ter sido usado. Gere um novo na tela"
          + " de cadastro.";
  private static final String WHATSAPP_EXPIRED =
      "Esse código venceu. Gere um novo na tela de cadastro.";
  private static final String WHATSAPP_CONFIRMED =
      "Pronto! Seu número foi confirmado. Pode voltar para a tela da BiniTech.";

  public record Policy(
      Duration ttl,
      int maxAttempts,
      int maxSends,
      Duration resendCooldown,
      int maxPerDestinationPerHour) {}

  private final VerificationRepositoryPort repository;
  private final Map<Channel, CodeSenderPort> senders = new EnumMap<>(Channel.class);
  private final CodeGeneratorPort generator;
  private final CodeHasherPort hasher;
  private final Policy policy;
  private final Clock clock;
  private final WhatsAppPort whatsApp;

  public VerificationUseCase(
      VerificationRepositoryPort repository,
      List<CodeSenderPort> senders,
      CodeGeneratorPort generator,
      CodeHasherPort hasher,
      Policy policy,
      Clock clock) {
    this(repository, senders, generator, hasher, policy, clock, null);
  }

  public VerificationUseCase(
      VerificationRepositoryPort repository,
      List<CodeSenderPort> senders,
      CodeGeneratorPort generator,
      CodeHasherPort hasher,
      Policy policy,
      Clock clock,
      WhatsAppPort whatsApp) {
    this.repository = repository;
    senders.forEach(sender -> this.senders.put(sender.channel(), sender));
    this.generator = generator;
    this.hasher = hasher;
    this.policy = policy;
    this.clock = clock;
    this.whatsApp = whatsApp;
  }

  @Override
  public VerificationView start(StartCommand command) {
    if (command.purpose() == null) {
      throw new BusinessException("Informe a finalidade da verificação.");
    }
    Destination destination = Destination.of(command.channel(), command.destination());
    boolean byWhatsApp = destination.channel() == Channel.WHATSAPP;
    if (byWhatsApp && whatsApp == null) {
      throw new ChannelUnavailableException(
          "A confirmação pelo WhatsApp não está disponível agora. Use o e-mail.");
    }
    CodeSenderPort sender = byWhatsApp ? null : senderFor(destination.channel());
    Instant now = clock.instant();
    long recent = repository.countCreatedSince(destination.value(), now.minus(DESTINATION_WINDOW));
    if (recent >= policy.maxPerDestinationPerHour()) {
      throw new TooManyRequestsException(
          "Muitos códigos pedidos para este destino. Tente de novo mais tarde.",
          DESTINATION_WINDOW.toSeconds());
    }
    String id = UUID.randomUUID().toString();
    String code = generator.generate();
    if (byWhatsApp) {
      Verification saved =
          repository.save(
              Verification.start(
                  id,
                  destination,
                  command.purpose(),
                  hasher.hash(WHATSAPP_SCOPE, code),
                  now,
                  policy.ttl()));
      return whatsAppView(saved, code);
    }
    Verification verification =
        Verification.start(
            id, destination, command.purpose(), hasher.hash(id, code), now, policy.ttl());
    Verification saved = repository.save(verification);
    sender.send(destination, code, command.purpose(), policy.ttl());
    return view(saved);
  }

  @Override
  public VerificationView get(String id) {
    Verification verification = find(id);
    if (verification.expireIfLate(clock.instant())) {
      repository.save(verification);
    }
    return view(verification);
  }

  @Override
  public VerificationView resend(String id) {
    Verification verification = find(id);
    Instant now = clock.instant();
    if (verification.expireIfLate(now)) {
      repository.save(verification);
      throw new VerificationExpiredException();
    }
    if (verification.getChannel() == Channel.WHATSAPP) {
      throw new BusinessException(
          "Na confirmação pelo WhatsApp não há reenvio: toque de novo no botão do WhatsApp.");
    }
    CodeSenderPort sender = senderFor(verification.getChannel());
    String code = generator.generate();
    verification.resend(
        hasher.hash(id, code), now, policy.ttl(), policy.resendCooldown(), policy.maxSends());
    Verification saved = repository.save(verification);
    sender.send(verification.getDestination(), code, verification.getPurpose(), policy.ttl());
    return view(saved);
  }

  @Override
  public CheckResult check(String id, String code) {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new BusinessException("Informe o código de 6 dígitos.");
    }
    Verification verification = find(id);
    if (verification.getChannel() == Channel.WHATSAPP) {
      throw new BusinessException("Esta verificação é confirmada pela mensagem no WhatsApp.");
    }
    Instant now = clock.instant();
    if (verification.expireIfLate(now)) {
      repository.save(verification);
      throw new VerificationExpiredException();
    }
    verification.ensureUsable();
    boolean matches = hasher.matches(id, code, verification.getCodeHash());
    boolean verified = verification.registerAttempt(matches, now, policy.maxAttempts());
    repository.save(verification);
    if (!verified) {
      int remaining = verification.remainingAttempts(policy.maxAttempts());
      if (remaining == 0) {
        throw new TooManyRequestsException(
            "Muitas tentativas com código errado. Peça um novo código.",
            Duration.between(now, verification.getExpiresAt()).toSeconds());
      }
      throw new InvalidCodeException(remaining);
    }
    return new CheckResult(
        verification.getId(),
        verification.getChannel(),
        verification.getDestination().value(),
        verification.getPurpose(),
        verification.getVerifiedAt());
  }

  @Override
  public void confirmByWhatsApp(String sender, String message) {
    if (whatsApp == null || sender == null || sender.isBlank()) {
      return;
    }
    Matcher matcher = CODE_IN_MESSAGE.matcher(message == null ? "" : message);
    if (!matcher.find()) {
      whatsApp.reply(sender, WHATSAPP_HELP);
      return;
    }
    List<Verification> candidates =
        repository.findPendingByCodeHash(hasher.hash(WHATSAPP_SCOPE, matcher.group(1)));
    Instant now = clock.instant();
    Optional<Verification> match =
        candidates.stream().filter(v -> v.getDestination().isSameMobile(sender)).findFirst();
    if (match.isEmpty()) {
      whatsApp.reply(sender, rejectOtherNumber(candidates, now));
      return;
    }
    Verification verification = match.get();
    if (verification.expireIfLate(now)) {
      repository.save(verification);
      whatsApp.reply(sender, WHATSAPP_EXPIRED);
      return;
    }
    verification.registerAttempt(true, now, policy.maxAttempts());
    repository.save(verification);
    whatsApp.reply(sender, WHATSAPP_CONFIRMED);
  }

  private String rejectOtherNumber(List<Verification> candidates, Instant now) {
    if (candidates.size() != 1) {
      return WHATSAPP_NOT_FOUND;
    }
    Verification only = candidates.get(0);
    if (only.expireIfLate(now)) {
      repository.save(only);
      return WHATSAPP_EXPIRED;
    }
    only.registerAttempt(false, now, policy.maxAttempts());
    repository.save(only);
    return "Esta mensagem precisa sair do WhatsApp do número "
        + only.getDestination().masked()
        + ", o mesmo informado no cadastro.";
  }

  private CodeSenderPort senderFor(Channel channel) {
    CodeSenderPort sender = senders.get(channel);
    if (sender == null) {
      throw new ChannelUnavailableException(
          channel == Channel.SMS
              ? "O envio por SMS não está disponível agora. Use o e-mail."
              : "O envio por e-mail não está disponível agora. Use o WhatsApp.");
    }
    return sender;
  }

  private Verification find(String id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Verificação não encontrada."));
  }

  private VerificationView whatsAppView(Verification verification, String code) {
    return new VerificationView(
        verification,
        null,
        verification.remainingAttempts(policy.maxAttempts()),
        0,
        code,
        whatsApp.chatLink("Confirmar meu número na BiniTech. Código " + code));
  }

  private VerificationView view(Verification verification) {
    if (verification.getChannel() == Channel.WHATSAPP) {
      return new VerificationView(
          verification, null, verification.remainingAttempts(policy.maxAttempts()), 0);
    }
    return new VerificationView(
        verification,
        verification.resendAvailableAt(policy.resendCooldown()),
        verification.remainingAttempts(policy.maxAttempts()),
        verification.remainingResends(policy.maxSends()));
  }
}
