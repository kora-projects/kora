package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopCamundaEngineLoggerFactory extends DefaultCamundaEngineLoggerFactory {

    public static final NoopCamundaEngineLoggerFactory INSTANCE = new NoopCamundaEngineLoggerFactory();

    private NoopCamundaEngineLoggerFactory() {}

    @Override
    public DefaultCamundaEngineLogger create(DefaultCamundaEngineTelemetry.TelemetryContext context, String javaDelegateName) {
        return NoopCamundaEngineLogger.INSTANCE;
    }

    public static final class NoopCamundaEngineLogger extends DefaultCamundaEngineLogger {

        public static final NoopCamundaEngineLogger INSTANCE = new NoopCamundaEngineLogger();

        private NoopCamundaEngineLogger() {
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
