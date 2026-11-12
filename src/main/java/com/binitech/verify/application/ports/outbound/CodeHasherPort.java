package com.binitech.verify.application.ports.outbound;

public interface CodeHasherPort {

  String hash(String verificationId, String code);

  boolean matches(String verificationId, String code, String hash);
}
