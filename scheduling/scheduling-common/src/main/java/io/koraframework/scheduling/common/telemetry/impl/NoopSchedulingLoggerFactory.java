package io.koraframework.scheduling.common.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopSchedulingLoggerFactory extends DefaultSchedulingLoggerFactory {

    public static final NoopSchedulingLoggerFactory INSTANCE = new NoopSchedulingLoggerFactory();

    private NoopSchedulingLoggerFactory() {}

    @Override
    public DefaultSchedulingLogger create(DefaultSchedulingTelemetry.TelemetryContext context) {
        return NoopSchedulingLogger.INSTANCE;
    }

    public static final class NoopSchedulingLogger extends DefaultSchedulingLogger {

        public static final NoopSchedulingLogger INSTANCE = new NoopSchedulingLogger();

        private NoopSchedulingLogger() {
            super(null, NOPLogger.NOP_LOGGER);
        }

        @Override
        public void logStart() {

        }

        @Override
        public void logEnd(@Nullable Throwable error, long durationInNanos) {

        }
    }
}
