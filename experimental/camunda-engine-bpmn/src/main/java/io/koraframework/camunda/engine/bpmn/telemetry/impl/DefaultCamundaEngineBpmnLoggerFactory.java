package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class DefaultCamundaEngineBpmnLoggerFactory {

    public static final DefaultCamundaEngineBpmnLoggerFactory INSTANCE = new DefaultCamundaEngineBpmnLoggerFactory();

    public DefaultCamundaEngineBpmnLogger create(DefaultCamundaEngineBpmnTelemetry.TelemetryContext context, String javaDelegateName) {
        return new DefaultCamundaEngineBpmnLogger(context, javaDelegateName, LoggerFactory.getLogger(javaDelegateName));
    }

    public static class DefaultCamundaEngineBpmnLogger {

        protected final DefaultCamundaEngineBpmnTelemetry.TelemetryContext context;
        protected final String javaDelegateName;
        protected final Logger logger;

        public DefaultCamundaEngineBpmnLogger(DefaultCamundaEngineBpmnTelemetry.TelemetryContext context,
                                             String javaDelegateName,
                                             Logger logger) {
            this.context = context;
            this.javaDelegateName = javaDelegateName;
            this.logger = logger;
        }

        public void logStart(DelegateExecution execution) {
            if (!this.logger.isInfoEnabled()) {
                return;
            }
            this.logger.atInfo()
                .addKeyValue("camundaExecution", structuredExecution(execution, null, 0))
                .addKeyValue("delegate", this.javaDelegateName)
                .log("Camunda BPMN Engine started");
        }

        public void logEnd(@Nullable DelegateExecution execution, @Nullable Throwable error, long processingTimeNanos) {
            if (error == null && !this.logger.isInfoEnabled()) {
                return;
            }
            if (error != null && !this.logger.isWarnEnabled()) {
                return;
            }

            this.logger.atLevel(error == null ? Level.INFO : Level.WARN)
                .addKeyValue("camundaExecution", structuredExecution(execution, error, processingTimeNanos))
                .addKeyValue("delegate", this.javaDelegateName)
                .setCause(this.context.config().logging().stacktrace() ? error : null)
                .log(error == null
                    ? "Camunda BPMN Engine finished delegate execution"
                    : "Camunda BPMN Engine failed delegate execution"
                );
        }

        protected StructuredArgumentWriter structuredExecution(@Nullable DelegateExecution execution,
                                                              @Nullable Throwable error,
                                                              long processingTimeNanos) {
            return gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("delegate", this.javaDelegateName);
                if (processingTimeNanos > 0) {
                    gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                }
                if (execution != null) {
                    gen.writeStringProperty("processBusinessKey", execution.getProcessBusinessKey());
                    gen.writeStringProperty("processInstanceId", execution.getProcessInstanceId());
                    gen.writeStringProperty("activityId", execution.getCurrentActivityId());
                    gen.writeStringProperty("activityName", execution.getCurrentActivityName());
                    gen.writeStringProperty("eventName", execution.getEventName());
                    gen.writeStringProperty("businessKey", execution.getBusinessKey());
                }
                if (error != null) {
                    var exceptionType = error.getClass().getCanonicalName();
                    if (exceptionType != null) {
                        gen.writeStringProperty("exceptionType", exceptionType);
                    }
                    if (!this.context.config().logging().stacktrace()) {
                        gen.writeStringProperty("exceptionMessage", error.getMessage());
                    }
                }
                gen.writeEndObject();
            };
        }
    }
}
