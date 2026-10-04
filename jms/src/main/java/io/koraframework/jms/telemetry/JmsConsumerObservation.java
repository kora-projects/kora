package io.koraframework.jms.telemetry;

import io.koraframework.common.telemetry.Observation;

import javax.jms.JMSException;

/**
 * Observes handler execution and transaction completion; end is called after commit or rollback.
 */
public interface JmsConsumerObservation extends Observation {

    void observeProcess() throws JMSException;
}
