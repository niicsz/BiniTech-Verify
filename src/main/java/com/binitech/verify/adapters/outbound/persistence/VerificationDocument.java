package com.binitech.verify.adapters.outbound.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "verifications")
@CompoundIndex(name = "destination_created", def = "{'destination': 1, 'createdAt': -1}")
public class VerificationDocument {
  @Id private String id;
  private String channel;
  private String destination;
  private String purpose;
  @Indexed private String codeHash;
  private String status;
  private int attempts;
  private int sends;
  private Instant createdAt;

  @Indexed(expireAfter = "1d")
  private Instant expiresAt;

  private Instant lastSentAt;
  private Instant verifiedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getDestination() {
    return destination;
  }

  public void setDestination(String destination) {
    this.destination = destination;
  }

  public String getPurpose() {
    return purpose;
  }

  public void setPurpose(String purpose) {
    this.purpose = purpose;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public void setCodeHash(String codeHash) {
    this.codeHash = codeHash;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public int getSends() {
    return sends;
  }

  public void setSends(int sends) {
    this.sends = sends;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getLastSentAt() {
    return lastSentAt;
  }

  public void setLastSentAt(Instant lastSentAt) {
    this.lastSentAt = lastSentAt;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public void setVerifiedAt(Instant verifiedAt) {
    this.verifiedAt = verifiedAt;
  }
}
