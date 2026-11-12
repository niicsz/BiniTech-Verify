package com.binitech.verify.adapters.outbound.email;

import com.binitech.verify.application.ports.outbound.CodeSenderPort;
import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import com.binitech.verify.domain.exception.ChannelUnavailableException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class ResendEmailSenderAdapter implements CodeSenderPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResendEmailSenderAdapter.class);

  private final RestClient client;
  private final String from;

  public ResendEmailSenderAdapter(RestClient client, String from) {
    this.client = client;
    this.from = from;
  }

  @Override
  public Channel channel() {
    return Channel.EMAIL;
  }

  @Override
  public void send(Destination destination, String code, Purpose purpose, Duration validity) {
    VerificationEmail email = VerificationEmail.of(code, purpose, validity);
    try {
      client
          .post()
          .uri("/emails")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new ResendRequest(
                  from, List.of(destination.value()), email.subject(), email.html(), email.text()))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      LOGGER.warn(
          "Falha ao enviar e-mail para {}: {}", destination.masked(), exception.getMessage());
      throw new ChannelUnavailableException(
          "Não foi possível enviar o e-mail agora. Tente de novo ou use o SMS.", exception);
    }
    LOGGER.info("E-mail de verificação enviado para {}", destination.masked());
  }

  record ResendRequest(String from, List<String> to, String subject, String html, String text) {}
}
