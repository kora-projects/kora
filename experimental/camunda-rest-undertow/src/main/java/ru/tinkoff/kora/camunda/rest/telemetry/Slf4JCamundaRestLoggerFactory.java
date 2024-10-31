package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;

import java.util.Objects;

public final class Slf4JCamundaRestLoggerFactory implements CamundaRestLoggerFactory {

    @Nullable
    @Override
    public CamundaRestLogger get(CamundaRestConfig.CamundaRestLoggerConfig logging) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new Slf4jCamundaRestLogger(logging.stacktrace(), logging.maskQueries(),
                logging.maskHeaders(), logging.mask(), logging.pathTemplate());
        } else {
            return null;
        }
    }
}
