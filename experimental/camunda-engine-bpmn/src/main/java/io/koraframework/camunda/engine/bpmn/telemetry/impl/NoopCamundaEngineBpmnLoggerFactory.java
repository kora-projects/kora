package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopCamundaEngineBpmnLoggerFactory extends DefaultCamundaEngineBpmnLoggerFactory {

    public static final NoopCamundaEngineBpmnLoggerFactory INSTANCE = new NoopCamundaEngineBpmnLoggerFactory();

    private NoopCamundaEngineBpmnLoggerFactory() {}

    @Override
    public DefaultCamundaEngineBpmnLogger create(DefaultCamundaEngineBpmnTelemetry.TelemetryContext context, String javaDelegateName) {
        return NoopCamundaEngineBpmnLogger.INSTANCE;
    }

    public static final class NoopCamundaEngineBpmnLogger extends DefaultCamundaEngineBpmnLogger {

        public static final NoopCamundaEngineBpmnLogger INSTANCE = new NoopCamundaEngineBpmnLogger();

        private NoopCamundaEngineBpmnLogger() {
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
