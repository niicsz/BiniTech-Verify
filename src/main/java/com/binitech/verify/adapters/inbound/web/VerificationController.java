package com.binitech.verify.adapters.inbound.web;

import com.binitech.verify.adapters.inbound.web.generated.api.VerificationsApi;
import com.binitech.verify.adapters.inbound.web.generated.model.CheckVerificationRequest;
import com.binitech.verify.adapters.inbound.web.generated.model.StartVerificationRequest;
import com.binitech.verify.adapters.inbound.web.generated.model.VerificationDTO;
import com.binitech.verify.adapters.inbound.web.generated.model.VerificationResultDTO;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort;
import com.binitech.verify.application.ports.inbound.VerificationUseCasePort.StartCommand;
import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Purpose;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerificationController implements VerificationsApi {

  private final VerificationUseCasePort verifications;

  public VerificationController(VerificationUseCasePort verifications) {
    this.verifications = verifications;
  }

  @Override
  public ResponseEntity<VerificationDTO> startVerification(StartVerificationRequest request) {
    StartCommand command =
        new StartCommand(
            request.getChannel() == null ? null : Channel.valueOf(request.getChannel().getValue()),
            request.getDestination(),
            request.getPurpose() == null ? null : Purpose.valueOf(request.getPurpose().getValue()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(VerificationWebMapper.toDto(verifications.start(command)));
  }

  @Override
  public ResponseEntity<VerificationDTO> getVerification(String id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(VerificationWebMapper.toDto(verifications.get(id)));
  }

  @Override
  public ResponseEntity<VerificationDTO> resendVerification(String id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(VerificationWebMapper.toDto(verifications.resend(id)));
  }

  @Override
  public ResponseEntity<VerificationResultDTO> checkVerification(
      String id, CheckVerificationRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(VerificationWebMapper.toDto(verifications.check(id, request.getCode())));
  }
}
