package com.binitech.verify.adapters.inbound.web;

import com.binitech.verify.adapters.inbound.web.generated.model.VerificationDTO;
import com.binitech.verify.adapters.inbound.web.generated.model.VerificationResultDTO;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort.CheckResult;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort.VerificationView;
import com.binitech.verify.domain.Verification;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class VerificationWebMapper {

  private VerificationWebMapper() {}

  static VerificationDTO toDto(VerificationView view) {
    Verification verification = view.verification();
    return new VerificationDTO()
        .id(verification.getId())
        .channel(
            com.binitech.verify.adapters.inbound.web.generated.model.Channel.fromValue(
                verification.getChannel().name()))
        .purpose(
            com.binitech.verify.adapters.inbound.web.generated.model.Purpose.fromValue(
                verification.getPurpose().name()))
        .status(
            com.binitech.verify.adapters.inbound.web.generated.model.VerificationStatus.fromValue(
                verification.getStatus().name()))
        .maskedDestination(verification.getDestination().masked())
        .expiresAt(time(verification.getExpiresAt()))
        .resendAvailableAt(time(view.resendAvailableAt()))
        .remainingAttempts(view.remainingAttempts())
        .remainingResends(view.remainingResends())
        .verifiedAt(time(verification.getVerifiedAt()))
        .whatsappCode(view.whatsappCode())
        .whatsappLink(view.whatsappLink());
  }

  static VerificationResultDTO toDto(CheckResult result) {
    return new VerificationResultDTO()
        .verificationId(result.verificationId())
        .channel(
            com.binitech.verify.adapters.inbound.web.generated.model.Channel.fromValue(
                result.channel().name()))
        .destination(result.destination())
        .purpose(
            com.binitech.verify.adapters.inbound.web.generated.model.Purpose.fromValue(
                result.purpose().name()))
        .verifiedAt(time(result.verifiedAt()));
  }

  private static OffsetDateTime time(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
