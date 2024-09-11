package ru.tinkoff.kora.http.client.common.telemetry;

import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

public class Sl4fjHttpClientLoggerFactory implements HttpClientLoggerFactory {

    @Override
    public HttpClientLogger get(HttpClientLoggerConfig logging, String clientName) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            var requestLog = LoggerFactory.getLogger(clientName + ".request");
            var responseLog = LoggerFactory.getLogger(clientName + ".response");
            final Set<String> maskedQueryParams = logging.maskQueries();
            final Set<String> maskedHeaders = logging.maskHeaders();
            return new Sl4fjHttpClientLogger(requestLog, responseLog, maskedQueryParams, maskedHeaders);
        } else {
            return null;
        }
    }
}
