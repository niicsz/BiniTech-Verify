package com.binitech.verify.adapters.outbound.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import com.binitech.verify.domain.exception.ChannelUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailSenderAdapterTest {

  private static final Destination EMAIL = Destination.of(Channel.EMAIL, "cliente@example.com");

  private MockRestServiceServer server;
  private ResendEmailSenderAdapter adapter;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
    server = MockRestServiceServer.bindTo(builder).build();
    adapter = new ResendEmailSenderAdapter(builder.build(), "BiniTech <nao-responda@binitech.com>");
  }

  @Test
  @DisplayName("Envia o código no assunto e no corpo, com o motivo da finalidade")
  void sendsEmail() {
    server
        .expect(requestTo("https://api.resend.com/emails"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.to[0]").value("cliente@example.com"))
        .andExpect(jsonPath("$.subject").value("Seu código BiniTech: 123456"))
        .andExpect(jsonPath("$.text", containsString("para entrar em um aparelho novo")))
        .andExpect(jsonPath("$.html", containsString("123456")))
        .andRespond(withSuccess("{\"id\":\"e1\"}", MediaType.APPLICATION_JSON));

    adapter.send(EMAIL, "123456", Purpose.NEW_DEVICE, Duration.ofMinutes(10));

    server.verify();
  }

  @Test
  @DisplayName("Erro do Resend vira canal indisponível")
  void providerError() {
    server.expect(requestTo("https://api.resend.com/emails")).andRespond(withBadRequest());

    assertThatThrownBy(() -> adapter.send(EMAIL, "123456", Purpose.SIGNUP, Duration.ofMinutes(10)))
        .isInstanceOf(ChannelUnavailableException.class);
  }
}
