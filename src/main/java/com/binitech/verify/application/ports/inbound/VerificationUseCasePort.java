package com.binitech.verify.application.ports.inbound;

import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Purpose;
import com.binitech.verify.domain.Verification;
import java.time.Instant;

public interface VerificationUseCasePort {

  record StartCommand(Channel channel, String destination, Purpose purpose) {}

  record VerificationView(
      Verification verification,
      Instant resendAvailableAt,
      int remainingAttempts,
      int remainingResends,
      String whatsappCode,
      String whatsappLink) {

    public VerificationView(
        Verification verification,
        Instant resendAvailableAt,
        int remainingAttempts,
        int remainingResends) {
      this(verification, resendAvailableAt, remainingAttempts, remainingResends, null, null);
    }
  }

  record CheckResult(
      String verificationId,
      Channel channel,
      String destination,
      Purpose purpose,
      Instant verifiedAt) {}

  VerificationView start(StartCommand command);

  VerificationView get(String id);

  VerificationView resend(String id);

  CheckResult check(String id, String code);

  void confirmByWhatsApp(String sender, String message);
}
