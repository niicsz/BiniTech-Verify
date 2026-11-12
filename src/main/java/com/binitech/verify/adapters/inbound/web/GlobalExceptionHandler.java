package com.binitech.verify.adapters.inbound.web;

import com.binitech.verify.adapters.inbound.web.generated.model.ErrorDTO;
import com.binitech.verify.domain.exception.BusinessException;
import com.binitech.verify.domain.exception.ChannelUnavailableException;
import com.binitech.verify.domain.exception.CodeAlreadyUsedException;
import com.binitech.verify.domain.exception.InvalidCodeException;
import com.binitech.verify.domain.exception.ResourceNotFoundException;
import com.binitech.verify.domain.exception.TooManyRequestsException;
import com.binitech.verify.domain.exception.VerificationExpiredException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ErrorDTO> notFound(ResourceNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ErrorDTO> badRequest(BusinessException exception) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<ErrorDTO> invalidBody(Exception exception) {
    return error(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dados inválidos. Confira os campos enviados.");
  }

  @ExceptionHandler(CodeAlreadyUsedException.class)
  ResponseEntity<ErrorDTO> alreadyUsed(CodeAlreadyUsedException exception) {
    return error(HttpStatus.CONFLICT, "CODE_ALREADY_USED", exception.getMessage());
  }

  @ExceptionHandler(VerificationExpiredException.class)
  ResponseEntity<ErrorDTO> expired(VerificationExpiredException exception) {
    return error(HttpStatus.GONE, "CODE_EXPIRED", exception.getMessage());
  }

  @ExceptionHandler(InvalidCodeException.class)
  ResponseEntity<ErrorDTO> invalidCode(InvalidCodeException exception) {
    ErrorDTO body = body("INVALID_CODE", exception.getMessage());
    body.setRemainingAttempts(exception.getRemainingAttempts());
    return ResponseEntity.status(422).body(body);
  }

  @ExceptionHandler(TooManyRequestsException.class)
  ResponseEntity<ErrorDTO> tooMany(TooManyRequestsException exception) {
    ErrorDTO body = body("TOO_MANY_REQUESTS", exception.getMessage());
    body.setRetryAfterSeconds((int) Math.min(Integer.MAX_VALUE, exception.getRetryAfterSeconds()));
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
        .body(body);
  }

  @ExceptionHandler(ChannelUnavailableException.class)
  ResponseEntity<ErrorDTO> unavailable(ChannelUnavailableException exception) {
    LOGGER.warn("Canal de envio indisponível: {}", exception.getMessage());
    return error(HttpStatus.SERVICE_UNAVAILABLE, "CHANNEL_UNAVAILABLE", exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorDTO> generic(Exception exception) {
    LOGGER.error("Erro inesperado no serviço de verificação", exception);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno. Tente de novo.");
  }

  private static ResponseEntity<ErrorDTO> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(body(code, message));
  }

  private static ErrorDTO body(String code, String message) {
    return new ErrorDTO().code(code).message(message).timestamp(OffsetDateTime.now(ZoneOffset.UTC));
  }
}
