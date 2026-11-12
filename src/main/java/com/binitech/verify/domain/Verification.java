package com.binitech.verify.domain;

import com.binitech.verify.domain.exception.CodeAlreadyUsedException;
import com.binitech.verify.domain.exception.TooManyRequestsException;
import com.binitech.verify.domain.exception.VerificationExpiredException;
import java.time.Duration;
import java.time.Instant;

public class Verification {
  private String id;
  private Destination destination;
  private Purpose purpose;
  private String codeHash;
  private VerificationStatus status;
  private int attempts;
  private int sends;
  private Instant createdAt;
  private Instant expiresAt;
  private Instant lastSentAt;
  private Instant verifiedAt;

  public record Snapshot(
      String id,
      Destination destination,
      Purpose purpose,
      String codeHash,
      VerificationStatus status,
      int attempts,
      int sends,
      Instant createdAt,
      Instant expiresAt,
      Instant lastSentAt,
      Instant verifiedAt) {}

  public static Verification start(
      String id,
      Destination destination,
      Purpose purpose,
      String codeHash,
      Instant now,
      Duration ttl) {
    return restore(
        new Snapshot(
            id,
            destination,
            purpose,
            codeHash,
            VerificationStatus.PENDING,
            0,
            1,
            now,
            now.plus(ttl),
            now,
            null));
  }

  public static Verification restore(Snapshot snapshot) {
    Verification verification = new Verification();
    verification.id = snapshot.id();
    verification.destination = snapshot.destination();
    verification.purpose = snapshot.purpose();
    verification.codeHash = snapshot.codeHash();
    verification.status = snapshot.status();
    verification.attempts = snapshot.attempts();
    verification.sends = snapshot.sends();
    verification.createdAt = snapshot.createdAt();
    verification.expiresAt = snapshot.expiresAt();
    verification.lastSentAt = snapshot.lastSentAt();
    verification.verifiedAt = snapshot.verifiedAt();
    return verification;
  }

  public Snapshot snapshot() {
    return new Snapshot(
        id,
        destination,
        purpose,
        codeHash,
        status,
        attempts,
        sends,
        createdAt,
        expiresAt,
        lastSentAt,
        verifiedAt);
  }

  public boolean expireIfLate(Instant now) {
    if (status == VerificationStatus.PENDING && !now.isBefore(expiresAt)) {
      status = VerificationStatus.EXPIRED;
      return true;
    }
    return false;
  }

  public void ensureUsable() {
    switch (status) {
      case VERIFIED -> throw new CodeAlreadyUsedException();
      case EXPIRED -> throw new VerificationExpiredException();
      case BLOCKED ->
          throw new TooManyRequestsException(
              "Muitas tentativas com código errado. Peça um novo código.",
              Duration.between(createdAt, expiresAt).toSeconds());
      case PENDING -> {}
    }
  }

  public Instant resendAvailableAt(Duration cooldown) {
    return lastSentAt.plus(cooldown);
  }

  public void resend(
      String newCodeHash, Instant now, Duration ttl, Duration cooldown, int maxSends) {
    ensureUsable();
    Instant available = resendAvailableAt(cooldown);
    if (now.isBefore(available)) {
      long seconds = Duration.between(now, available).toSeconds() + 1;
      throw new TooManyRequestsException(
          "Aguarde " + seconds + " segundos para pedir um novo código.", seconds);
    }
    if (sends >= maxSends) {
      throw new TooManyRequestsException(
          "Limite de reenvios atingido. Comece uma nova verificação em alguns minutos.",
          Duration.between(now, expiresAt).toSeconds());
    }
    codeHash = newCodeHash;
    sends++;
    expiresAt = now.plus(ttl);
    lastSentAt = now;
  }

  public boolean registerAttempt(boolean codeMatches, Instant now, int maxAttempts) {
    ensureUsable();
    if (codeMatches) {
      status = VerificationStatus.VERIFIED;
      verifiedAt = now;
      return true;
    }
    attempts++;
    if (attempts >= maxAttempts) {
      status = VerificationStatus.BLOCKED;
    }
    return false;
  }

  public int remainingAttempts(int maxAttempts) {
    return Math.max(0, maxAttempts - attempts);
  }

  public int remainingResends(int maxSends) {
    return Math.max(0, maxSends - sends);
  }

  public String getId() {
    return id;
  }

  public Destination getDestination() {
    return destination;
  }

  public Channel getChannel() {
    return destination.channel();
  }

  public Purpose getPurpose() {
    return purpose;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public VerificationStatus getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }
}
