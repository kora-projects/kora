package io.koraframework.jms.telemetry;

import io.koraframework.common.telemetry.Observation;

import javax.jms.JMSException;

public interface JmsConsumerObservation extends Observation {
    void observeProcess() throws JMSException;
}
