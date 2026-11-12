package com.binitech.verify.adapters.outbound.email;

import com.binitech.verify.domain.Purpose;
import java.time.Duration;

record VerificationEmail(String subject, String html, String text) {

  static VerificationEmail of(String code, Purpose purpose, Duration validity) {
    String reason =
        switch (purpose) {
          case SIGNUP -> "para confirmar o seu cadastro";
          case NEW_DEVICE -> "para entrar em um aparelho novo";
          case ACCOUNT_RECOVERY -> "para recuperar o acesso à sua conta";
        };
    long minutes = validity.toMinutes();
    String text =
        "Use o código "
            + code
            + " "
            + reason
            + ". Ele vale por "
            + minutes
            + " minutos. Se não foi você, ignore este e-mail.";
    String html =
        "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;"
            + "padding:24px;color:#1e293b\">"
            + "<p style=\"font-size:15px;line-height:1.6\">Use o código abaixo "
            + reason
            + ".</p>"
            + "<p style=\"font-size:32px;font-weight:bold;letter-spacing:8px;text-align:center;"
            + "margin:24px 0\">"
            + code
            + "</p>"
            + "<p style=\"font-size:14px;color:#475569\">Ele vale por "
            + minutes
            + " minutos. Não compartilhe com ninguém.</p>"
            + "<p style=\"font-size:13px;color:#94a3b8\">Se não foi você, ignore este e-mail.</p>"
            + "</div>";
    return new VerificationEmail("Seu código BiniTech: " + code, html, text);
  }
}
