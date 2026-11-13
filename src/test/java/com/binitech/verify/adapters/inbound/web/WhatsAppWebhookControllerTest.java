package com.binitech.verify.adapters.inbound.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.binitech.verify.application.ports.inbound.VerificationUseCasePort;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WhatsAppWebhookControllerTest {

  private static final String SECRET = "segredo-do-app";
  private static final String VERIFY_TOKEN = "token-de-verificacao";
  private static final String PAYLOAD =
      """
      {"object":"whatsapp_business_account","entry":[{"changes":[{"field":"messages","value":{
      "messages":[{"from":"551187654321","type":"text",
      "text":{"body":"Confirmar meu número na BiniTech. Código 123456"}}]}}]}]}
      """;

  private final VerificationUseCasePort useCase = mock(VerificationUseCasePort.class);

  private MockMvc mvc(String secret, String token) {
    return MockMvcBuilders.standaloneSetup(new WhatsAppWebhookController(useCase, secret, token))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  private static String sign(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("Devolve o desafio quando o token de verificação confere")
  void verifiesSubscription() throws Exception {
    mvc(SECRET, VERIFY_TOKEN)
        .perform(
            get("/webhooks/whatsapp")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", VERIFY_TOKEN)
                .param("hub.challenge", "1158201444"))
        .andExpect(status().isOk())
        .andExpect(content().string("1158201444"));
  }

  @Test
  @DisplayName("Recusa a inscrição com token errado")
  void rejectsWrongVerifyToken() throws Exception {
    mvc(SECRET, VERIFY_TOKEN)
        .perform(
            get("/webhooks/whatsapp")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", "outro")
                .param("hub.challenge", "1"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Mensagem com assinatura certa vai para o caso de uso")
  void handlesSignedMessage() throws Exception {
    mvc(SECRET, VERIFY_TOKEN)
        .perform(
            post("/webhooks/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", sign(PAYLOAD))
                .content(PAYLOAD))
        .andExpect(status().isOk());

    verify(useCase)
        .confirmByWhatsApp("551187654321", "Confirmar meu número na BiniTech. Código 123456");
  }

  @Test
  @DisplayName("Assinatura ausente ou errada é recusada sem processar nada")
  void rejectsBadSignature() throws Exception {
    MockMvc mvc = mvc(SECRET, VERIFY_TOKEN);
    mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/webhooks/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", sign(PAYLOAD + " "))
                .content(PAYLOAD))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(useCase);
  }

  @Test
  @DisplayName("Sem segredo configurado o webhook não existe")
  void disabledWithoutSecret() throws Exception {
    mvc("", "")
        .perform(
            post("/webhooks/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", sign(PAYLOAD))
                .content(PAYLOAD))
        .andExpect(status().isNotFound());

    verifyNoInteractions(useCase);
  }
}
