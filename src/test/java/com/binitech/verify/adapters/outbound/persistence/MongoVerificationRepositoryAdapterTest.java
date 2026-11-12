package com.binitech.verify.adapters.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import com.binitech.verify.domain.Verification;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MongoVerificationRepositoryAdapterTest {

  @Test
  @DisplayName("Documento guarda e devolve todos os campos da verificação")
  void roundTrip() {
    Verification original =
        Verification.start(
            "v1",
            Destination.of(Channel.SMS, "11987654321"),
            Purpose.ACCOUNT_RECOVERY,
            "hash",
            Instant.parse("2026-11-12T12:00:00Z"),
            Duration.ofMinutes(10));
    original.registerAttempt(false, Instant.parse("2026-11-12T12:01:00Z"), 5);

    VerificationDocument document = MongoVerificationRepositoryAdapter.toDocument(original);
    Verification restored = MongoVerificationRepositoryAdapter.toDomain(document);

    assertThat(document.getDestination()).isEqualTo("5511987654321");
    assertThat(document.getChannel()).isEqualTo("SMS");
    assertThat(restored.snapshot()).isEqualTo(original.snapshot());
  }
}
