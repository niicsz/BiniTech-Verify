package com.binitech.verify.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.binitech.verify.application.ports.inbound.VerificationUseCasePort.CheckResult;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort.StartCommand;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort.VerificationView;
import com.binitech.verify.application.ports.outbound.CodeHasherPort;
import com.binitech.verify.application.ports.outbound.CodeSenderPort;
import com.binitech.verify.application.ports.outbound.VerificationRepositoryPort;
import com.binitech.verify.application.ports.outbound.WhatsAppPort;
import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import com.binitech.verify.domain.Verification;
import com.binitech.verify.domain.VerificationStatus;
import com.binitech.verify.domain.exception.BusinessException;
import com.binitech.verify.domain.exception.ChannelUnavailableException;
import com.binitech.verify.domain.exception.CodeAlreadyUsedException;
import com.binitech.verify.domain.exception.InvalidCodeException;
import com.binitech.verify.domain.exception.ResourceNotFoundException;
import com.binitech.verify.domain.exception.TooManyRequestsException;
import com.binitech.verify.domain.exception.VerificationExpiredException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificationUseCaseTest {

  private static final VerificationUseCase.Policy POLICY =
      new VerificationUseCase.Policy(Duration.ofMinutes(10), 5, 3, Duration.ofSeconds(60), 5);

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T12:00:00Z"));
  private final InMemoryRepository repository = new InMemoryRepository();
  private final RecordingSender sms = new RecordingSender(Channel.SMS);
  private final RecordingSender email = new RecordingSender(Channel.EMAIL);
  private final Deque<String> codes = new ArrayDeque<>();
  private VerificationUseCase useCase;

  @BeforeEach
  void setUp() {
    codes.addAll(List.of("123456", "654321", "111222", "333444"));
    useCase = build(List.of(sms, email));
  }

  private VerificationUseCase build(List<CodeSenderPort> senders) {
    return new VerificationUseCase(
        repository, senders, codes::poll, new PlainHasher(), POLICY, clock);
  }

  private VerificationView startSms() {
    return useCase.start(new StartCommand(Channel.SMS, "(11) 98765-4321", Purpose.SIGNUP));
  }

  @Test
  @DisplayName("Envia o código pelo canal escolhido e guarda só o hash")
  void startSendsCodeAndStoresHash() {
    VerificationView view = startSms();

    assertThat(sms.sent).containsExactly("5511987654321:123456");
    assertThat(email.sent).isEmpty();
    Verification stored = repository.store.get(view.verification().getId());
    assertThat(stored.getCodeHash()).isEqualTo("hash:" + stored.getId() + ":123456");
    assertThat(stored.getStatus()).isEqualTo(VerificationStatus.PENDING);
    assertThat(view.remainingAttempts()).isEqualTo(5);
    assertThat(view.remainingResends()).isEqualTo(2);
    assertThat(view.resendAvailableAt()).isEqualTo(Instant.parse("2026-11-12T12:01:00Z"));
    assertThat(stored.getExpiresAt()).isEqualTo(Instant.parse("2026-11-12T12:10:00Z"));
  }

  @Test
  @DisplayName("Usuário pode escolher receber por e-mail")
  void startByEmail() {
    useCase.start(new StartCommand(Channel.EMAIL, "Cliente@Example.com", Purpose.NEW_DEVICE));

    assertThat(email.sent).containsExactly("cliente@example.com:123456");
    assertThat(sms.sent).isEmpty();
  }

  @Test
  @DisplayName("Canal sem provedor configurado responde indisponível sem gravar nada")
  void unavailableChannel() {
    VerificationUseCase emailOnly = build(List.of(email));

    assertThatThrownBy(
            () -> emailOnly.start(new StartCommand(Channel.SMS, "11987654321", Purpose.SIGNUP)))
        .isInstanceOf(ChannelUnavailableException.class)
        .hasMessageContaining("Use o e-mail");
    assertThat(repository.store).isEmpty();
  }

  @Test
  @DisplayName("Limita a 5 códigos por destino por hora")
  void limitsPerDestination() {
    for (int i = 0; i < 4; i++) {
      codes.add("00000" + i);
    }
    for (int i = 0; i < 5; i++) {
      startSms();
    }

    assertThatThrownBy(this::startSms)
        .isInstanceOf(TooManyRequestsException.class)
        .satisfies(
            e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(3600));
  }

  @Test
  @DisplayName("Código certo verifica e devolve o destino normalizado")
  void checkSucceeds() {
    String id = startSms().verification().getId();
    clock.advance(Duration.ofMinutes(2));

    CheckResult result = useCase.check(id, "123456");

    assertThat(result.destination()).isEqualTo("5511987654321");
    assertThat(result.channel()).isEqualTo(Channel.SMS);
    assertThat(result.purpose()).isEqualTo(Purpose.SIGNUP);
    assertThat(result.verifiedAt()).isEqualTo(Instant.parse("2026-11-12T12:02:00Z"));
    assertThat(repository.store.get(id).getStatus()).isEqualTo(VerificationStatus.VERIFIED);
  }

  @Test
  @DisplayName("Código certo não vale duas vezes")
  void codeIsSingleUse() {
    String id = startSms().verification().getId();
    useCase.check(id, "123456");

    assertThatThrownBy(() -> useCase.check(id, "123456"))
        .isInstanceOf(CodeAlreadyUsedException.class);
  }

  @Test
  @DisplayName("Código errado informa as tentativas restantes")
  void wrongCodeCountsAttempts() {
    String id = startSms().verification().getId();

    assertThatThrownBy(() -> useCase.check(id, "999999"))
        .isInstanceOf(InvalidCodeException.class)
        .satisfies(e -> assertThat(((InvalidCodeException) e).getRemainingAttempts()).isEqualTo(4));
  }

  @Test
  @DisplayName("Na quinta tentativa errada bloqueia, e nem o código certo passa depois")
  void blocksAfterMaxAttempts() {
    String id = startSms().verification().getId();
    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> useCase.check(id, "999999"))
          .isInstanceOf(InvalidCodeException.class);
    }

    assertThatThrownBy(() -> useCase.check(id, "999999"))
        .isInstanceOf(TooManyRequestsException.class);
    assertThat(repository.store.get(id).getStatus()).isEqualTo(VerificationStatus.BLOCKED);
    assertThatThrownBy(() -> useCase.check(id, "123456"))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  @DisplayName("Depois de 10 minutos o código vence")
  void expires() {
    String id = startSms().verification().getId();
    clock.advance(Duration.ofMinutes(10));

    assertThatThrownBy(() -> useCase.check(id, "123456"))
        .isInstanceOf(VerificationExpiredException.class);
    assertThat(repository.store.get(id).getStatus()).isEqualTo(VerificationStatus.EXPIRED);
    assertThat(useCase.get(id).verification().getStatus()).isEqualTo(VerificationStatus.EXPIRED);
  }

  @Test
  @DisplayName("Reenvio antes de 60 segundos é recusado com o tempo de espera")
  void resendCooldown() {
    String id = startSms().verification().getId();
    clock.advance(Duration.ofSeconds(20));

    assertThatThrownBy(() -> useCase.resend(id))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessageContaining("41 segundos");
  }

  @Test
  @DisplayName("Reenvio troca o código e o anterior deixa de valer")
  void resendReplacesCode() {
    String id = startSms().verification().getId();
    clock.advance(Duration.ofSeconds(61));

    VerificationView view = useCase.resend(id);

    assertThat(sms.sent).containsExactly("5511987654321:123456", "5511987654321:654321");
    assertThat(view.remainingResends()).isEqualTo(1);
    assertThatThrownBy(() -> useCase.check(id, "123456")).isInstanceOf(InvalidCodeException.class);
    assertThat(useCase.check(id, "654321").verificationId()).isEqualTo(id);
  }

  @Test
  @DisplayName("No máximo 3 envios por verificação")
  void limitsResends() {
    String id = startSms().verification().getId();
    clock.advance(Duration.ofSeconds(61));
    useCase.resend(id);
    clock.advance(Duration.ofSeconds(61));
    useCase.resend(id);
    clock.advance(Duration.ofSeconds(61));

    assertThatThrownBy(() -> useCase.resend(id))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessageContaining("Limite de reenvios");
  }

  @Test
  @DisplayName("Verificação inexistente responde não encontrada")
  void notFound() {
    assertThatThrownBy(() -> useCase.check("nao-existe", "123456"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("Código fora do formato é recusado sem gastar tentativa")
  void rejectsMalformedCode() {
    String id = startSms().verification().getId();

    assertThatThrownBy(() -> useCase.check(id, "12ab")).hasMessageContaining("6 dígitos");
    assertThat(useCase.get(id).remainingAttempts()).isEqualTo(5);
  }

  private final FakeWhatsApp whatsApp = new FakeWhatsApp();

  private VerificationView startWhatsApp() {
    VerificationUseCase withWhatsApp =
        new VerificationUseCase(
            repository,
            List.of(sms, email),
            codes::poll,
            new PlainHasher(),
            POLICY,
            clock,
            whatsApp);
    useCase = withWhatsApp;
    return useCase.start(new StartCommand(Channel.WHATSAPP, "(11) 98765-4321", Purpose.SIGNUP));
  }

  @Test
  @DisplayName("WhatsApp devolve código e link, não envia nada e guarda só o hash")
  void whatsAppStartReturnsLink() {
    VerificationView view = startWhatsApp();

    assertThat(view.whatsappCode()).isEqualTo("123456");
    assertThat(view.whatsappLink())
        .isEqualTo("link:Confirmar meu número na BiniTech. Código 123456");
    assertThat(view.remainingResends()).isZero();
    assertThat(view.resendAvailableAt()).isNull();
    assertThat(sms.sent).isEmpty();
    assertThat(repository.store.get(view.verification().getId()).getCodeHash())
        .isEqualTo("hash:whatsapp:123456");
  }

  @Test
  @DisplayName("Sem WhatsApp configurado o canal responde indisponível")
  void whatsAppUnavailable() {
    assertThatThrownBy(
            () -> useCase.start(new StartCommand(Channel.WHATSAPP, "11987654321", Purpose.SIGNUP)))
        .isInstanceOf(ChannelUnavailableException.class)
        .hasMessageContaining("WhatsApp");
  }

  @Test
  @DisplayName("Mensagem do mesmo número confirma, mesmo sem o nono dígito")
  void whatsAppConfirms() {
    String id = startWhatsApp().verification().getId();

    useCase.confirmByWhatsApp("551187654321", "Confirmar meu número na BiniTech. Código 123456");

    assertThat(useCase.get(id).verification().getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(whatsApp.replies).singleElement().asString().contains("Pronto!");
  }

  @Test
  @DisplayName("Mensagem de outro número não confirma e gasta uma tentativa")
  void whatsAppOtherNumber() {
    String id = startWhatsApp().verification().getId();

    useCase.confirmByWhatsApp("5521999998888", "Código 123456");

    VerificationView view = useCase.get(id);
    assertThat(view.verification().getStatus()).isEqualTo(VerificationStatus.PENDING);
    assertThat(view.remainingAttempts()).isEqualTo(4);
    assertThat(whatsApp.replies).singleElement().asString().contains("(11) *****-4321");
  }

  @Test
  @DisplayName("Código vencido no WhatsApp marca como vencido e avisa")
  void whatsAppExpired() {
    String id = startWhatsApp().verification().getId();
    clock.advance(Duration.ofMinutes(11));

    useCase.confirmByWhatsApp("5511987654321", "Código 123456");

    assertThat(repository.store.get(id).getStatus()).isEqualTo(VerificationStatus.EXPIRED);
    assertThat(whatsApp.replies).singleElement().asString().contains("venceu");
  }

  @Test
  @DisplayName("Código usado ou mensagem sem código recebem orientação")
  void whatsAppUsedOrNoCode() {
    startWhatsApp();
    useCase.confirmByWhatsApp("5511987654321", "Código 123456");

    useCase.confirmByWhatsApp("5511987654321", "Código 123456");
    useCase.confirmByWhatsApp("5511987654321", "oi, tudo bem?");

    assertThat(whatsApp.replies).hasSize(3);
    assertThat(whatsApp.replies.get(1)).contains("Não encontrei");
    assertThat(whatsApp.replies.get(2)).contains("só confirma cadastros");
  }

  @Test
  @DisplayName("Verificação pelo WhatsApp não aceita conferência por código nem reenvio")
  void whatsAppHasNoCheckOrResend() {
    String id = startWhatsApp().verification().getId();

    assertThatThrownBy(() -> useCase.check(id, "123456"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("WhatsApp");
    assertThatThrownBy(() -> useCase.resend(id))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("WhatsApp");
  }

  static final class FakeWhatsApp implements WhatsAppPort {
    final List<String> replies = new ArrayList<>();

    @Override
    public String chatLink(String message) {
      return "link:" + message;
    }

    @Override
    public void reply(String to, String message) {
      replies.add(message);
    }
  }

  static final class InMemoryRepository implements VerificationRepositoryPort {
    final Map<String, Verification> store = new HashMap<>();

    @Override
    public Verification save(Verification verification) {
      store.put(verification.getId(), Verification.restore(verification.snapshot()));
      return verification;
    }

    @Override
    public Optional<Verification> findById(String id) {
      return Optional.ofNullable(store.get(id)).map(v -> Verification.restore(v.snapshot()));
    }

    @Override
    public List<Verification> findPendingByCodeHash(String codeHash) {
      return store.values().stream()
          .filter(v -> v.getCodeHash().equals(codeHash))
          .filter(v -> v.getStatus() == VerificationStatus.PENDING)
          .map(v -> Verification.restore(v.snapshot()))
          .toList();
    }

    @Override
    public long countCreatedSince(String destination, Instant since) {
      return store.values().stream()
          .map(Verification::snapshot)
          .filter(s -> s.destination().value().equals(destination))
          .filter(s -> !s.createdAt().isBefore(since))
          .count();
    }
  }

  static final class RecordingSender implements CodeSenderPort {
    final Channel channel;
    final List<String> sent = new ArrayList<>();

    RecordingSender(Channel channel) {
      this.channel = channel;
    }

    @Override
    public Channel channel() {
      return channel;
    }

    @Override
    public void send(Destination destination, String code, Purpose purpose, Duration validity) {
      sent.add(destination.value() + ":" + code);
    }
  }

  static final class PlainHasher implements CodeHasherPort {
    @Override
    public String hash(String verificationId, String code) {
      return "hash:" + verificationId + ":" + code;
    }

    @Override
    public boolean matches(String verificationId, String code, String hash) {
      return hash(verificationId, code).equals(hash);
    }
  }

  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
