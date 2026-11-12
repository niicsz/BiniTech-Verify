package com.binitech.verify.adapters.outbound.persistence;

import com.binitech.verify.application.ports.outbound.VerificationRepositoryPort;
import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import com.binitech.verify.domain.Verification;
import com.binitech.verify.domain.VerificationStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class MongoVerificationRepositoryAdapter implements VerificationRepositoryPort {

  private final MongoTemplate mongo;

  public MongoVerificationRepositoryAdapter(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @Override
  public Verification save(Verification verification) {
    return toDomain(mongo.save(toDocument(verification)));
  }

  @Override
  public Optional<Verification> findById(String id) {
    return Optional.ofNullable(mongo.findById(id, VerificationDocument.class))
        .map(MongoVerificationRepositoryAdapter::toDomain);
  }

  @Override
  public long countCreatedSince(String destination, Instant since) {
    return mongo.count(
        Query.query(Criteria.where("destination").is(destination).and("createdAt").gte(since)),
        VerificationDocument.class);
  }

  static VerificationDocument toDocument(Verification verification) {
    Verification.Snapshot s = verification.snapshot();
    VerificationDocument document = new VerificationDocument();
    document.setId(s.id());
    document.setChannel(s.destination().channel().name());
    document.setDestination(s.destination().value());
    document.setPurpose(s.purpose().name());
    document.setCodeHash(s.codeHash());
    document.setStatus(s.status().name());
    document.setAttempts(s.attempts());
    document.setSends(s.sends());
    document.setCreatedAt(s.createdAt());
    document.setExpiresAt(s.expiresAt());
    document.setLastSentAt(s.lastSentAt());
    document.setVerifiedAt(s.verifiedAt());
    return document;
  }

  static Verification toDomain(VerificationDocument document) {
    return Verification.restore(
        new Verification.Snapshot(
            document.getId(),
            new Destination(Channel.valueOf(document.getChannel()), document.getDestination()),
            Purpose.valueOf(document.getPurpose()),
            document.getCodeHash(),
            VerificationStatus.valueOf(document.getStatus()),
            document.getAttempts(),
            document.getSends(),
            document.getCreatedAt(),
            document.getExpiresAt(),
            document.getLastSentAt(),
            document.getVerifiedAt()));
  }
}
