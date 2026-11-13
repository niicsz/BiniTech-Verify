package com.binitech.verify.adapters.outbound.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WhatsAppCloudAdapterTest {

  private static final String GRAPH = "https://graph.facebook.com/v23.0";

  @Test
  @DisplayName("Monta o link wa.me com a mensagem codificada")
  void buildsChatLink() {
    WhatsAppCloudAdapter adapter = new WhatsAppCloudAdapter(null, "+55 (11) 91234-5678", "");

    assertThat(adapter.chatLink("Confirmar meu número. Código 123456"))
        .isEqualTo(
            "https://wa.me/5511912345678?text=Confirmar%20meu%20n%C3%BAmero.%20C%C3%B3digo%20123456");
  }

  @Test
  @DisplayName("Número da empresa sem DDD é recusado na subida")
  void rejectsShortBusinessNumber() {
    assertThatThrownBy(() -> new WhatsAppCloudAdapter(null, "1234", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Responde pela API Cloud no número de quem mandou")
  void replies() {
    RestClient.Builder builder = RestClient.builder().baseUrl(GRAPH);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(GRAPH + "/998877/messages"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json(
                    """
                    {"messaging_product":"whatsapp","to":"551187654321","type":"text",
                     "text":{"body":"Pronto!"}}
                    """))
        .andRespond(withSuccess());

    new WhatsAppCloudAdapter(builder.build(), "5511912345678", "998877")
        .reply("551187654321", "Pronto!");

    server.verify();
  }

  @Test
  @DisplayName("Falha ao responder não derruba a confirmação")
  void replyFailureIsIgnored() {
    RestClient.Builder builder = RestClient.builder().baseUrl(GRAPH);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(GRAPH + "/998877/messages")).andRespond(withServerError());

    WhatsAppCloudAdapter adapter =
        new WhatsAppCloudAdapter(builder.build(), "5511912345678", "998877");

    assertThatCode(() -> adapter.reply("551187654321", "Pronto!")).doesNotThrowAnyException();
  }
}
