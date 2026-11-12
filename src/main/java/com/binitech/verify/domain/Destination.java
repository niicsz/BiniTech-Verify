package com.binitech.verify.domain;

import com.binitech.verify.domain.exception.BusinessException;
import java.util.Locale;
import java.util.regex.Pattern;

public record Destination(Channel channel, String value) {
  private static final Pattern EMAIL =
      Pattern.compile("^[a-z0-9._%+-]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,24}$");
  private static final int MAX_EMAIL_LENGTH = 254;

  public static Destination of(Channel channel, String raw) {
    if (channel == null) {
      throw new BusinessException("Escolha receber o código por e-mail ou por SMS.");
    }
    String trimmed = raw == null ? "" : raw.strip();
    return switch (channel) {
      case EMAIL -> email(trimmed);
      case SMS -> mobile(trimmed);
    };
  }

  private static Destination email(String raw) {
    String value = raw.toLowerCase(Locale.ROOT);
    if (value.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(value).matches()) {
      throw new BusinessException("Informe um e-mail válido.");
    }
    return new Destination(Channel.EMAIL, value);
  }

  private static Destination mobile(String raw) {
    String digits = raw.replaceAll("\\D", "");
    if (digits.startsWith("55") && digits.length() == 13) {
      digits = digits.substring(2);
    }
    if (digits.length() != 11
        || digits.charAt(2) != '9'
        || digits.charAt(0) == '0'
        || digits.charAt(1) == '0') {
      throw new BusinessException("Informe um celular com DDD, por exemplo (11) 91234-5678.");
    }
    return new Destination(Channel.SMS, "55" + digits);
  }

  public String masked() {
    if (channel == Channel.EMAIL) {
      int at = value.indexOf('@');
      return value.charAt(0) + "***" + value.substring(at);
    }
    return "(" + value.substring(2, 4) + ") *****-" + value.substring(value.length() - 4);
  }
}
