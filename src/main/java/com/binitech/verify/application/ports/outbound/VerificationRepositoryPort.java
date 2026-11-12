package com.binitech.verify.application.ports.outbound;

import com.binitech.verify.domain.Verification;
import java.time.Instant;
import java.util.Optional;

public interface VerificationRepositoryPort {

  Verification save(Verification verification);

  Optional<Verification> findById(String id);

  long countCreatedSince(String destination, Instant since);
}
