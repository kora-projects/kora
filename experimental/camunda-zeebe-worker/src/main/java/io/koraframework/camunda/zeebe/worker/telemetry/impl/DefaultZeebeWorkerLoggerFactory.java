package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.koraframework.camunda.zeebe.worker.exception.JobWorkerException;
import io.koraframework.logging.common.arg.StructuredArgument;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultZeebeWorkerLoggerFactory {

    public static final DefaultZeebeWorkerLoggerFactory INSTANCE = new DefaultZeebeWorkerLoggerFactory();

    public DefaultZeebeWorkerLogger create(DefaultZeebeWorkerTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger("io.koraframework.camunda.zeebe.worker." + context.workerType());
        return new DefaultZeebeWorkerLogger(logger);
    }

    public static class DefaultZeebeWorkerLogger {

        protected final Logger logger;

        public DefaultZeebeWorkerLogger(Logger logger) {
            this.logger = logger;
        }

        public void logJobHandle(ActivatedJob job) {
            if (!this.logger.isInfoEnabled()) {
                return;
            }
            var variables = this.logger.isDebugEnabled() ? job.getVariables() : null;
            this.logger.atInfo()
                .addKeyValue("zeebeJob", StructuredArgument.value(gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("type", job.getType());
                    gen.writeStringProperty("bpmnProcessId", job.getBpmnProcessId());
                    gen.writeNumberProperty("key", job.getKey());
                    gen.writeNumberProperty("processInstanceKey", job.getProcessInstanceKey());
                    if (variables != null) {
                        gen.writeStringProperty("variables", variables);
                    }
                    gen.writeEndObject();
                }))
                .log("Zeebe JobWorker started Job");
        }

        public void logJobEnd(ActivatedJob job, @Nullable Throwable error, boolean failedByUser, long processingTimeNanos) {
            if (error == null && !failedByUser && !this.logger.isInfoEnabled()) {
                return;
            }
            if ((error != null || failedByUser) && !this.logger.isWarnEnabled()) {
                return;
            }
            var data = StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("type", job.getType());
                gen.writeStringProperty("bpmnProcessId", job.getBpmnProcessId());
                gen.writeNumberProperty("key", job.getKey());
                gen.writeNumberProperty("processInstanceKey", job.getProcessInstanceKey());
                gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                if (error instanceof JobWorkerException je) {
                    gen.writeStringProperty("errorCode", je.getCode());
                    gen.writeStringProperty("errorMessage", je.getMessage());
                }
                if (error != null) {
                    var exceptionType = error.getClass().getCanonicalName();
                    if (exceptionType != null) {
                        gen.writeStringProperty("exceptionType", exceptionType);
                    }
                } else if (failedByUser) {
                    gen.writeStringProperty("exceptionType", "ErrorStep");
                }
                gen.writeEndObject();
            });
            if (error != null) {
                this.logger.atWarn()
                    .setCause(error)
                    .addKeyValue("zeebeJob", data)
                    .log("Zeebe JobWorker failed Job");
            } else if (failedByUser) {
                this.logger.atWarn()
                    .addKeyValue("zeebeJob", data)
                    .log("Zeebe JobWorker failed Job");
            } else {
                this.logger.atInfo()
                    .addKeyValue("zeebeJob", data)
                    .log("Zeebe JobWorker completed Job");
            }
        }
    }
}
