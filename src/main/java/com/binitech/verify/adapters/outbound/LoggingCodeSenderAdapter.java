package com.binitech.verify.adapters.outbound;

import com.binitech.verify.application.ports.outbound.CodeSenderPort;
import com.binitech.verify.domain.Channel;
import com.binitech.verify.domain.Destination;
import com.binitech.verify.domain.Purpose;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingCodeSenderAdapter implements CodeSenderPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingCodeSenderAdapter.class);

  private final Channel channel;

  public LoggingCodeSenderAdapter(Channel channel) {
    this.channel = channel;
  }

  @Override
  public Channel channel() {
    return channel;
  }

  @Override
  public void send(Destination destination, String code, Purpose purpose, Duration validity) {
    LOGGER.warn(
        "[desenvolvimento] código {} para {} por {} ({})",
        code,
        destination.masked(),
        channel,
        purpose);
  }
}
