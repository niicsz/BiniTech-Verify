package com.binitech.verify.application.usecases;

import com.binitech.verify.application.ports.inbound.VerificationUseCasePort;
import com.binitech.verify.application.ports.outbound.CodeGeneratorPort;
import com.binitech.verify.application.ports.outbound.CodeHasherPort;
import com.binitech.verify.application.ports.outbound.CodeSenderPort;
import com.binitech.verify.application.ports.outbound.VerificationRepositoryPort;
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
import java.util.UUID;
import java.util.regex.Pattern;

public class VerificationUseCase implements VerificationUseCasePort {

  private static final Pattern CODE = Pattern.compile("\\d{6}");
  private static final Duration DESTINATION_WINDOW = Duration.ofHours(1);

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

  public VerificationUseCase(
      VerificationRepositoryPort repository,
      List<CodeSenderPort> senders,
      CodeGeneratorPort generator,
      CodeHasherPort hasher,
      Policy policy,
      Clock clock) {
    this.repository = repository;
    senders.forEach(sender -> this.senders.put(sender.channel(), sender));
    this.generator = generator;
    this.hasher = hasher;
    this.policy = policy;
    this.clock = clock;
  }

  @Override
  public VerificationView start(StartCommand command) {
    if (command.purpose() == null) {
      throw new BusinessException("Informe a finalidade da verificação.");
    }
    Destination destination = Destination.of(command.channel(), command.destination());
    CodeSenderPort sender = senderFor(destination.channel());
    Instant now = clock.instant();
    long recent = repository.countCreatedSince(destination.value(), now.minus(DESTINATION_WINDOW));
    if (recent >= policy.maxPerDestinationPerHour()) {
      throw new TooManyRequestsException(
          "Muitos códigos pedidos para este destino. Tente de novo mais tarde.",
          DESTINATION_WINDOW.toSeconds());
    }
    String id = UUID.randomUUID().toString();
    String code = generator.generate();
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

  private CodeSenderPort senderFor(Channel channel) {
    CodeSenderPort sender = senders.get(channel);
    if (sender == null) {
      throw new ChannelUnavailableException(
          channel == Channel.SMS
              ? "O envio por SMS não está disponível agora. Use o e-mail."
              : "O envio por e-mail não está disponível agora. Use o SMS.");
    }
    return sender;
  }

  private Verification find(String id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Verificação não encontrada."));
  }

  private VerificationView view(Verification verification) {
    return new VerificationView(
        verification,
        verification.resendAvailableAt(policy.resendCooldown()),
        verification.remainingAttempts(policy.maxAttempts()),
        verification.remainingResends(policy.maxSends()));
  }
}
