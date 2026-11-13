package com.binitech.verify.application.ports.outbound;

public interface WhatsAppPort {

  String chatLink(String message);

  void reply(String to, String message);
}
