package com.binitech.verify.adapters.outbound.security;

import com.binitech.verify.application.ports.outbound.CodeHasherPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacCodeHasherAdapter implements CodeHasherPort {

  private static final String ALGORITHM = "HmacSHA256";
  private static final int MIN_PEPPER_LENGTH = 32;

  private final SecretKeySpec key;

  public HmacCodeHasherAdapter(String pepper) {
    if (pepper == null || pepper.length() < MIN_PEPPER_LENGTH) {
      throw new IllegalArgumentException("VERIFY_CODE_PEPPER deve ter ao menos 32 caracteres.");
    }
    this.key = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  @Override
  public String hash(String verificationId, String code) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      byte[] digest = mac.doFinal((verificationId + ":" + code).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC indisponível", exception);
    }
  }

  @Override
  public boolean matches(String verificationId, String code, String hash) {
    if (hash == null) {
      return false;
    }
    return MessageDigest.isEqual(
        hash(verificationId, code).getBytes(StandardCharsets.UTF_8),
        hash.getBytes(StandardCharsets.UTF_8));
  }
}
