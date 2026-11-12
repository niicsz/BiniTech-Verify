package com.binitech.verify.adapters.outbound.sms;

import com.binitech.verify.application.ports.outbound.CodeSenderPort;
import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import com.binitech.verify.domain.exception.ChannelUnavailableException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class MobizonSmsSenderAdapter implements CodeSenderPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(MobizonSmsSenderAdapter.class);
  private static final String UNAVAILABLE =
      "Não foi possível enviar o SMS agora. Tente de novo ou use o e-mail.";

  private final RestClient client;
  private final String apiKey;
  private final String senderId;

  public MobizonSmsSenderAdapter(RestClient client, String apiKey, String senderId) {
    this.client = client;
    this.apiKey = apiKey;
    this.senderId = senderId;
  }

  @Override
  public Channel channel() {
    return Channel.SMS;
  }

  @Override
  public void send(Destination destination, String code, Purpose purpose, Duration validity) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("recipient", destination.value());
    form.add("text", SmsText.of(code, validity));
    if (senderId != null && !senderId.isBlank()) {
      form.add("from", senderId);
    }
    MobizonResponse response;
    try {
      response =
          client
              .post()
              .uri(
                  uri ->
                      uri.path("/service/message/sendSmsMessage")
                          .queryParam("output", "json")
                          .queryParam("api", "v1")
                          .queryParam("apiKey", apiKey)
                          .build())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(MobizonResponse.class);
    } catch (RestClientException exception) {
      LOGGER.warn(
          "Falha ao chamar a Mobizon para {}: {}", destination.masked(), exception.getMessage());
      throw new ChannelUnavailableException(UNAVAILABLE, exception);
    }
    if (response == null || response.code() == null || response.code() != 0) {
      LOGGER.warn(
          "Mobizon recusou o SMS para {}: código {} {}",
          destination.masked(),
          response == null ? null : response.code(),
          response == null ? null : response.message());
      throw new ChannelUnavailableException(UNAVAILABLE);
    }
    LOGGER.info(
        "SMS de verificação enviado para {} (mensagem {})",
        destination.masked(),
        response.data() == null ? null : response.data().get("messageId"));
  }

  record MobizonResponse(Integer code, Map<String, Object> data, String message) {}
}
