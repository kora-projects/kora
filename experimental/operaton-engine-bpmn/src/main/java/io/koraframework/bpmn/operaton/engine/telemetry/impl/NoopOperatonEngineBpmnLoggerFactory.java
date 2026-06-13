package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopOperatonEngineBpmnLoggerFactory extends DefaultOperatonEngineBpmnLoggerFactory {

    public static final NoopOperatonEngineBpmnLoggerFactory INSTANCE = new NoopOperatonEngineBpmnLoggerFactory();

    private NoopOperatonEngineBpmnLoggerFactory() {}

    @Override
    public DefaultOperatonEngineBpmnLogger create(DefaultOperatonEngineBpmnTelemetry.TelemetryContext context, String javaDelegateName) {
        return NoopOperatonEngineBpmnLogger.INSTANCE;
    }

    public static final class NoopOperatonEngineBpmnLogger extends DefaultOperatonEngineBpmnLogger {

        public static final NoopOperatonEngineBpmnLogger INSTANCE = new NoopOperatonEngineBpmnLogger();

        private NoopOperatonEngineBpmnLogger() {
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
