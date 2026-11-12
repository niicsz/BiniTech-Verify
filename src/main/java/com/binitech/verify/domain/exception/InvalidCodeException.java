package com.binitech.verify.domain.exception;

public class InvalidCodeException extends RuntimeException {
  private final int remainingAttempts;

  public InvalidCodeException(int remainingAttempts) {
    super(
        remainingAttempts == 1
            ? "Código errado. Resta 1 tentativa."
            : "Código errado. Restam " + remainingAttempts + " tentativas.");
    this.remainingAttempts = remainingAttempts;
  }

  public int getRemainingAttempts() {
    return remainingAttempts;
  }
}
