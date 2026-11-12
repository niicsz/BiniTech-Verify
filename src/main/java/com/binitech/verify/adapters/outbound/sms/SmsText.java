package com.binitech.verify.adapters.outbound.sms;

import java.time.Duration;

final class SmsText {

  private SmsText() {}

  static String of(String code, Duration validity) {
    return "BiniTech: seu codigo e "
        + code
        + ". Vale por "
        + validity.toMinutes()
        + " minutos. Nao compartilhe com ninguem.";
  }
}
