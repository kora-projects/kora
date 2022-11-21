package ru.tinkoff.kora.jms;

import ru.tinkoff.kora.jms.telemetry.JmsConsumerTelemetryFactory;

import javax.jms.ConnectionFactory;

public class JmsMessageListenerContainerFactory {
    private final ConnectionFactory jmsConnectionFactory;
    private final JmsConsumerTelemetryFactory telemetry;

    public JmsMessageListenerContainerFactory(ConnectionFactory jmsConnectionFactory, JmsConsumerTelemetryFactory telemetry) {
        this.jmsConnectionFactory = jmsConnectionFactory;
        this.telemetry = telemetry;
    }

    public JmsMessageListenerContainer build(JmsListenerContainerConfig config, JmsMessageListener messageListener) {
        return new JmsMessageListenerContainer(this.jmsConnectionFactory, config, messageListener, this.telemetry);
    }
}
