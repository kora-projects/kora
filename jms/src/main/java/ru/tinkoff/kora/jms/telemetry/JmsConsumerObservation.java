package ru.tinkoff.kora.jms.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;

import javax.jms.JMSException;

public interface JmsConsumerObservation extends Observation {
    void observeProcess() throws JMSException;
}
