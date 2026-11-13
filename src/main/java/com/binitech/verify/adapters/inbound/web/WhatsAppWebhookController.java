package com.binitech.verify.adapters.inbound.web;

import com.binitech.verify.adapters.inbound.web.generated.api.WebhooksApi;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class WhatsAppWebhookController implements WebhooksApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppWebhookController.class);
  private static final String ALGORITHM = "HmacSHA256";
  private static final String SIGNATURE_PREFIX = "sha256=";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final VerificationUseCasePort verifications;
  private final SecretKeySpec appSecret;
  private final byte[] verifyToken;

  public WhatsAppWebhookController(
      VerificationUseCasePort verifications,
      @Value("${whatsapp.app-secret:}") String appSecret,
      @Value("${whatsapp.verify-token:}") String verifyToken) {
    this.verifications = verifications;
    boolean enabled = !appSecret.isBlank() && !verifyToken.isBlank();
    this.appSecret =
        enabled ? new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM) : null;
    this.verifyToken = enabled ? verifyToken.getBytes(StandardCharsets.UTF_8) : null;
  }

  @Override
  public ResponseEntity<String> verifyWhatsAppWebhook(
      String hubMode, String hubVerifyToken, String hubChallenge) {
    if (appSecret == null) {
      return ResponseEntity.notFound().build();
    }
    boolean valid =
        "subscribe".equals(hubMode)
            && hubVerifyToken != null
            && hubChallenge != null
            && MessageDigest.isEqual(verifyToken, hubVerifyToken.getBytes(StandardCharsets.UTF_8));
    if (!valid) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(hubChallenge);
  }

  @Override
  public ResponseEntity<Void> receiveWhatsAppWebhook(String body, String signature) {
    if (appSecret == null) {
      return ResponseEntity.notFound().build();
    }
    if (!validSignature(body, signature)) {
      LOGGER.warn("Webhook do WhatsApp com assinatura inválida recusado");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    JsonNode root;
    try {
      root = JSON.readTree(body);
    } catch (JacksonException exception) {
      LOGGER.warn("Webhook do WhatsApp com JSON inválido: {}", exception.getOriginalMessage());
      return ResponseEntity.ok().build();
    }
    JsonNode entries = root.path("entry");
    for (int e = 0; e < entries.size(); e++) {
      JsonNode changes = entries.get(e).path("changes");
      for (int c = 0; c < changes.size(); c++) {
        JsonNode messages = changes.get(c).path("value").path("messages");
        for (int m = 0; m < messages.size(); m++) {
          handle(messages.get(m));
        }
      }
    }
    return ResponseEntity.ok().build();
  }

  private void handle(JsonNode message) {
    String sender = message.path("from").asString("");
    String text =
        switch (message.path("type").asString("")) {
          case "text" -> message.path("text").path("body").asString("");
          case "button" -> message.path("button").path("text").asString("");
          default -> "";
        };
    try {
      verifications.confirmByWhatsApp(sender, text);
    } catch (RuntimeException exception) {
      LOGGER.error("Falha ao tratar mensagem do WhatsApp", exception);
    }
  }

  private boolean validSignature(String body, String signature) {
    if (body == null || signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(appSecret);
      String expected =
          SIGNATURE_PREFIX
              + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC indisponível", exception);
    }
  }
}
