package com.binitech.verify.adapters.outbound.sms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class MobizonSmsSenderAdapterTest {

  private static final Destination PHONE = Destination.of(Channel.SMS, "11987654321");

  private MockRestServiceServer server;
  private MobizonSmsSenderAdapter adapter;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mobizon.com.br");
    server = MockRestServiceServer.bindTo(builder).build();
    adapter = new MobizonSmsSenderAdapter(builder.build(), "chave-teste", "BiniTech");
  }

  @Test
  @DisplayName("Envia recipient, texto sem acento e remetente para a Mobizon")
  void sendsSms() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("recipient", "5511987654321");
    form.add(
        "text", "BiniTech: seu codigo e 123456. Vale por 10 minutos. Nao compartilhe com ninguem.");
    form.add("from", "BiniTech");
    server
        .expect(requestTo(startsWith("https://api.mobizon.com.br/service/message/sendSmsMessage")))
        .andExpect(method(HttpMethod.POST))
        .andExpect(queryParam("apiKey", "chave-teste"))
        .andExpect(queryParam("output", "json"))
        .andExpect(queryParam("api", "v1"))
        .andExpect(content().formData(form))
        .andRespond(
            withSuccess(
                "{\"code\":0,\"data\":{\"campaignId\":1,\"messageId\":2,\"status\":2},\"message\":\"\"}",
                MediaType.APPLICATION_JSON));

    adapter.send(PHONE, "123456", Purpose.SIGNUP, Duration.ofMinutes(10));

    server.verify();
  }

  @Test
  @DisplayName("Código de erro da Mobizon vira canal indisponível")
  void providerErrorCode() {
    server
        .expect(requestTo(startsWith("https://api.mobizon.com.br/service/message/sendSmsMessage")))
        .andRespond(
            withSuccess(
                "{\"code\":1,\"data\":{\"recipient\":\"invalido\"},\"message\":\"erro\"}",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> adapter.send(PHONE, "123456", Purpose.SIGNUP, Duration.ofMinutes(10)))
        .isInstanceOf(ChannelUnavailableException.class);
  }

  @Test
  @DisplayName("Falha HTTP da Mobizon vira canal indisponível")
  void httpError() {
    server
        .expect(requestTo(startsWith("https://api.mobizon.com.br/service/message/sendSmsMessage")))
        .andRespond(withServerError());

    assertThatThrownBy(() -> adapter.send(PHONE, "123456", Purpose.SIGNUP, Duration.ofMinutes(10)))
        .isInstanceOf(ChannelUnavailableException.class)
        .hasMessageContaining("use o e-mail");
  }
}
