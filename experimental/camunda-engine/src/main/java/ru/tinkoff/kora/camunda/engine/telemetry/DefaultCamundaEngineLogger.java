package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.engine.Camunda;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

public final class DefaultCamundaEngineLogger implements CamundaEngineLogger {

    private static final Logger logger = LoggerFactory.getLogger(Camunda.class);

    private final boolean logStacktrace;

    public DefaultCamundaEngineLogger(boolean stacktrace) {
        this.logStacktrace = stacktrace;
    }

    @Override
    public void logStart(String javaDelegateName, DelegateExecution execution) {
        if (!logger.isInfoEnabled()) {
            return;
        }

        var marker = StructuredArgument.marker("execution", gen -> {
            gen.writeStartObject();
            gen.writeStringField("processBusinessKey", execution.getProcessBusinessKey());
            gen.writeStringField("processInstanceId", execution.getProcessInstanceId());
            gen.writeStringField("activityId", execution.getCurrentActivityId());
            gen.writeStringField("activityName", execution.getCurrentActivityName());
            gen.writeStringField("eventName", execution.getEventName());
            gen.writeStringField("businessKey", execution.getBusinessKey());
            gen.writeEndObject();
        });

        logger.info(marker, "Camunda Engine started delegate {}", javaDelegateName);
    }

    @Override
    public void logEnd(String javaDelegateName, DelegateExecution execution, long processingTime, @Nullable Throwable exception) {
        if (!logger.isWarnEnabled()) {
            return;
        }

        var marker = StructuredArgument.marker("execution", gen -> {
            gen.writeStartObject();
            gen.writeStringField("processBusinessKey", execution.getProcessBusinessKey());
            gen.writeStringField("processInstanceId", execution.getProcessInstanceId());
            gen.writeStringField("activityId", execution.getCurrentActivityId());
            gen.writeStringField("activityName", execution.getCurrentActivityName());
            gen.writeStringField("eventName", execution.getEventName());
            gen.writeStringField("businessKey", execution.getBusinessKey());
            if (exception != null) {
                var exceptionType = exception.getClass().getCanonicalName();
                gen.writeStringField("exceptionType", exceptionType);
            }
            gen.writeEndObject();
        });

        if (this.logStacktrace && exception != null) {
            logger.warn(marker, "Camunda Engine failed delegate {}", javaDelegateName, exception);
        } else if (exception != null) {
            logger.warn(marker, "Camunda Engine failed delegate {} with message: {}", javaDelegateName, exception.getMessage());
        } else {
            logger.warn(marker, "Camunda Engine finished delegate {}", javaDelegateName);
        }
    }
}
