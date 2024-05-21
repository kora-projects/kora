package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;

import java.util.Objects;

public final class DefaultCamundaRestLoggerFactory implements CamundaRestLoggerFactory {

    @Nullable
    @Override
    public CamundaRestLogger get(CamundaRestConfig.CamundaRestLogConfig logging) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new DefaultCamundaRestLogger(logging.stacktrace());
        } else {
            return null;
        }
    }
}
