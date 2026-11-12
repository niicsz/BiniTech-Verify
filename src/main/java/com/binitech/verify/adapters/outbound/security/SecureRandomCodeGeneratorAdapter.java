package com.binitech.verify.adapters.outbound.security;

import com.binitech.verify.application.ports.outbound.CodeGeneratorPort;
import java.security.SecureRandom;

public class SecureRandomCodeGeneratorAdapter implements CodeGeneratorPort {

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    return String.format("%06d", random.nextInt(1_000_000));
  }
}
