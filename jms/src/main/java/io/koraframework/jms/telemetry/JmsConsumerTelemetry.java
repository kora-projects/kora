package io.koraframework.jms.telemetry;

import javax.jms.JMSException;
import javax.jms.Message;

public interface JmsConsumerTelemetry {

    JmsConsumerObservation observe(Message message) throws JMSException;

    /**
     * Called after a connection/session/consumer failure, before reconnecting.
     */
    default void observeConnectionError(Throwable error) {}
}
