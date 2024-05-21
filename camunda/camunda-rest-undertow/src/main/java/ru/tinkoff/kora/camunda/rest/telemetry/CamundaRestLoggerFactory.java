package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;

public interface CamundaRestLoggerFactory {

    @Nullable
    CamundaRestLogger get(CamundaRestConfig.CamundaRestLogConfig logging);
}
