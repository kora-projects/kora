package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopOperatonEngineLoggerFactory extends DefaultOperatonEngineLoggerFactory {

    public static final NoopOperatonEngineLoggerFactory INSTANCE = new NoopOperatonEngineLoggerFactory();

    private NoopOperatonEngineLoggerFactory() {}

    @Override
    public DefaultOperatonEngineLogger create(DefaultOperatonEngineTelemetry.TelemetryContext context, String javaDelegateName) {
        return NoopOperatonEngineLogger.INSTANCE;
    }

    public static final class NoopOperatonEngineLogger extends DefaultOperatonEngineLogger {

        public static final NoopOperatonEngineLogger INSTANCE = new NoopOperatonEngineLogger();

        private NoopOperatonEngineLogger() {
            super(null, "none", NOPLogger.NOP_LOGGER);
        }

        @Override
        public void logStart(DelegateExecution execution) {

        }

        @Override
        public void logEnd(@Nullable DelegateExecution execution, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
