package io.koraframework.jms.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

import javax.jms.Message;

public final class NoopJmsConsumerLoggerFactory extends DefaultJmsConsumerLoggerFactory {

    public static final NoopJmsConsumerLoggerFactory INSTANCE = new NoopJmsConsumerLoggerFactory();

    private NoopJmsConsumerLoggerFactory() {}

    @Override
    public DefaultJmsConsumerLogger create(DefaultJmsConsumerTelemetry.TelemetryContext context) {
        return NoopJmsConsumerLogger.INSTANCE;
    }

    public static final class NoopJmsConsumerLogger extends DefaultJmsConsumerLogger {

        public static final NoopJmsConsumerLogger INSTANCE = new NoopJmsConsumerLogger();

        private NoopJmsConsumerLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultJmsConsumerTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logStart(Message message, String destination) {}

        @Override
        public void logProcess(Message message, String destination) {}

        @Override
        public void logEnd(Message message, String destination, long processingTimeNanos, @Nullable Throwable exception) {}
    }
}
