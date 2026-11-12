package com.binitech.verify.adapters.inbound.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.binitech.verify.application.ports.inbound.VerificationUseCasePort;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort.CheckResult;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort.VerificationView;
import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import com.binitech.verify.domain.Verification;
import com.binitech.verify.domain.exception.ChannelUnavailableException;
import com.binitech.verify.domain.exception.InvalidCodeException;
import com.binitech.verify.domain.exception.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VerificationControllerTest {

  private final VerificationUseCasePort useCase = mock(VerificationUseCasePort.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new VerificationController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static VerificationView view() {
    Verification verification =
        Verification.start(
            "v1",
            Destination.of(Channel.SMS, "11987654321"),
            Purpose.SIGNUP,
            "hash",
            Instant.parse("2026-11-12T12:00:00Z"),
            Duration.ofMinutes(10));
    return new VerificationView(verification, Instant.parse("2026-11-12T12:01:00Z"), 5, 2);
  }

  @Test
  @DisplayName("POST cria a verificação e devolve o destino mascarado, nunca o código")
  void startsVerification() throws Exception {
    when(useCase.start(any())).thenReturn(view());

    mvc.perform(
            post("/api/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"channel\":\"SMS\",\"destination\":\"(11) 98765-4321\",\"purpose\":\"SIGNUP\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("v1"))
        .andExpect(jsonPath("$.maskedDestination").value("(11) *****-4321"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.remainingResends").value(2))
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @DisplayName("Canal inválido no corpo responde 400")
  void invalidChannel() throws Exception {
    mvc.perform(
            post("/api/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"FAX\",\"destination\":\"x\",\"purpose\":\"SIGNUP\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("Código certo devolve o destino verificado")
  void checksCode() throws Exception {
    when(useCase.check("v1", "123456"))
        .thenReturn(
            new CheckResult(
                "v1",
                Channel.SMS,
                "5511987654321",
                Purpose.SIGNUP,
                Instant.parse("2026-11-12T12:02:00Z")));

    mvc.perform(
            post("/api/verifications/v1/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.destination").value("5511987654321"))
        .andExpect(jsonPath("$.channel").value("SMS"));
  }

  @Test
  @DisplayName("Código errado responde 422 com as tentativas restantes")
  void wrongCode() throws Exception {
    when(useCase.check(eq("v1"), any())).thenThrow(new InvalidCodeException(3));

    mvc.perform(
            post("/api/verifications/v1/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INVALID_CODE"))
        .andExpect(jsonPath("$.remainingAttempts").value(3));
  }

  @Test
  @DisplayName("Código fora do formato é barrado pelo contrato com 400")
  void malformedCode() throws Exception {
    mvc.perform(
            post("/api/verifications/v1/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"12\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Limite atingido responde 429 com Retry-After")
  void tooManyRequests() throws Exception {
    when(useCase.resend("v1")).thenThrow(new TooManyRequestsException("Aguarde", 41));

    mvc.perform(post("/api/verifications/v1/resend"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "41"))
        .andExpect(jsonPath("$.retryAfterSeconds").value(41));
  }

  @Test
  @DisplayName("Provedor fora do ar responde 503")
  void channelUnavailable() throws Exception {
    when(useCase.start(any())).thenThrow(new ChannelUnavailableException("SMS fora do ar"));

    mvc.perform(
            post("/api/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"channel\":\"SMS\",\"destination\":\"11987654321\",\"purpose\":\"SIGNUP\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CHANNEL_UNAVAILABLE"));
  }
}
