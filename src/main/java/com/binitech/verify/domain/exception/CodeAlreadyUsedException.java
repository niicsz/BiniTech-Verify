package com.binitech.verify.domain.exception;

public class CodeAlreadyUsedException extends RuntimeException {
  public CodeAlreadyUsedException() {
    super("Este código já foi usado. Peça um novo.");
  }
}
