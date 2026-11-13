package com.binitech.verify.config;

import com.binitech.verify.adapters.outbound.LoggingCodeSenderAdapter;
import com.binitech.verify.adapters.outbound.email.ResendEmailSenderAdapter;
import com.binitech.verify.adapters.outbound.security.HmacCodeHasherAdapter;
import com.binitech.verify.adapters.outbound.security.SecureRandomCodeGeneratorAdapter;
import com.binitech.verify.adapters.outbound.sms.MobizonSmsSenderAdapter;
import com.binitech.verify.adapters.outbound.whatsapp.WhatsAppCloudAdapter;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort;
import com.binitech.verify.application.ports.outbound.CodeSenderPort;
import com.binitech.verify.application.ports.outbound.VerificationRepositoryPort;
import com.binitech.verify.application.ports.outbound.WhatsAppPort;
import com.binitech.verify.application.usecases.VerificationUseCase;
import com.binitech.verify.domain.Channel;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BeanConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(BeanConfiguration.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  VerificationUseCasePort verificationUseCase(
      VerificationRepositoryPort repository,
      Clock clock,
      @Value("${verify.code-pepper}") String pepper,
      @Value("${verify.code-ttl}") Duration ttl,
      @Value("${verify.max-attempts}") int maxAttempts,
      @Value("${verify.max-sends}") int maxSends,
      @Value("${verify.resend-cooldown}") Duration resendCooldown,
      @Value("${verify.max-per-destination-per-hour}") int maxPerDestination,
      @Value("${verify.log-codes:false}") boolean logCodes,
      @Value("${mobizon.base-url}") String mobizonUrl,
      @Value("${mobizon.api-key:}") String mobizonKey,
      @Value("${mobizon.sender-id:}") String mobizonSender,
      @Value("${resend.api-key:}") String resendKey,
      @Value("${resend.from}") String resendFrom,
      @Value("${whatsapp.business-number:}") String whatsappNumber,
      @Value("${whatsapp.app-secret:}") String whatsappSecret,
      @Value("${whatsapp.verify-token:}") String whatsappVerifyToken,
      @Value("${whatsapp.graph-url}") String whatsappGraphUrl,
      @Value("${whatsapp.phone-number-id:}") String whatsappPhoneId,
      @Value("${whatsapp.access-token:}") String whatsappToken) {
    List<CodeSenderPort> senders = new ArrayList<>();
    if (!mobizonKey.isBlank()) {
      senders.add(
          new MobizonSmsSenderAdapter(client(mobizonUrl).build(), mobizonKey, mobizonSender));
    } else if (logCodes) {
      senders.add(new LoggingCodeSenderAdapter(Channel.SMS));
    }
    if (!resendKey.isBlank()) {
      senders.add(
          new ResendEmailSenderAdapter(
              client("https://api.resend.com")
                  .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resendKey)
                  .build(),
              resendFrom));
    } else if (logCodes) {
      senders.add(new LoggingCodeSenderAdapter(Channel.EMAIL));
    }
    WhatsAppPort whatsApp = null;
    if (!whatsappNumber.isBlank() && !whatsappSecret.isBlank() && !whatsappVerifyToken.isBlank()) {
      boolean canReply = !whatsappPhoneId.isBlank() && !whatsappToken.isBlank();
      whatsApp =
          new WhatsAppCloudAdapter(
              canReply
                  ? client(whatsappGraphUrl)
                      .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + whatsappToken)
                      .build()
                  : null,
              whatsappNumber,
              whatsappPhoneId);
      if (!canReply) {
        LOGGER.warn("WhatsApp sem WHATSAPP_ACCESS_TOKEN: confirma números, mas não responde");
      }
    }
    List<String> active = new ArrayList<>(senders.stream().map(s -> s.channel().name()).toList());
    if (whatsApp != null) {
      active.add(Channel.WHATSAPP.name());
    }
    LOGGER.info(
        "Canais de envio ativos: {}{}",
        active,
        logCodes ? " (códigos no log: só para desenvolvimento)" : "");
    return new VerificationUseCase(
        repository,
        senders,
        new SecureRandomCodeGeneratorAdapter(),
        new HmacCodeHasherAdapter(pepper),
        new VerificationUseCase.Policy(
            ttl, maxAttempts, maxSends, resendCooldown, maxPerDestination),
        clock,
        whatsApp);
  }

  private static RestClient.Builder client(String url) {
    if (!url.matches("https?://.+")) {
      throw new IllegalArgumentException("URL de provedor inválida: " + url);
    }
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(url.replaceAll("/$", ""))
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.USER_AGENT, "BiniTech-Verify/1.0");
  }
}
