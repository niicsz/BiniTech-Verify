package com.binitech.verify.domain.exception;

public class VerificationExpiredException extends RuntimeException {
  public VerificationExpiredException() {
    super("O código venceu. Peça um novo.");
  }
}
