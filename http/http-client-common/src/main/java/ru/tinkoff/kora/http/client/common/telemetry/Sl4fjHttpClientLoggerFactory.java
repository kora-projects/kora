package ru.tinkoff.kora.http.client.common.telemetry;

import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class Sl4fjHttpClientLoggerFactory implements HttpClientLoggerFactory {
    @Override
    public HttpClientLogger get(TelemetryConfig.LogConfig logging, String clientName) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            var requestLog = LoggerFactory.getLogger(clientName + ".request");
            var responseLog = LoggerFactory.getLogger(clientName + ".response");
            return new Sl4fjHttpClientLogger(requestLog, responseLog);
        } else {
            return null;
        }
    }
}
