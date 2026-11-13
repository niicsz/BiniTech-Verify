package com.binitech.verify.adapters.outbound.whatsapp;

import com.binitech.verify.application.ports.outbound.WhatsAppPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class WhatsAppCloudAdapter implements WhatsAppPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppCloudAdapter.class);

  private final RestClient client;
  private final String businessNumber;
  private final String phoneNumberId;

  public WhatsAppCloudAdapter(RestClient client, String businessNumber, String phoneNumberId) {
    String digits = businessNumber == null ? "" : businessNumber.replaceAll("\\D", "");
    if (digits.length() < 10) {
      throw new IllegalArgumentException(
          "WHATSAPP_BUSINESS_NUMBER deve ter o número com DDI e DDD, por exemplo 5511912345678.");
    }
    this.client = client;
    this.businessNumber = digits;
    this.phoneNumberId = phoneNumberId;
  }

  @Override
  public String chatLink(String message) {
    return "https://wa.me/"
        + businessNumber
        + "?text="
        + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
  }

  @Override
  public void reply(String to, String message) {
    if (client == null || phoneNumberId == null || phoneNumberId.isBlank()) {
      return;
    }
    try {
      client
          .post()
          .uri("/{phoneNumberId}/messages", phoneNumberId)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              Map.of(
                  "messaging_product",
                  "whatsapp",
                  "to",
                  to,
                  "type",
                  "text",
                  "text",
                  Map.of("body", message)))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      LOGGER.warn("Falha ao responder no WhatsApp: {}", exception.getMessage());
    }
  }
}
