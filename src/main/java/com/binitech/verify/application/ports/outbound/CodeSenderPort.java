package com.binitech.verify.application.ports.outbound;

import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import java.time.Duration;

public interface CodeSenderPort {

  Channel channel();

  void send(Destination destination, String code, Purpose purpose, Duration validity);
}
