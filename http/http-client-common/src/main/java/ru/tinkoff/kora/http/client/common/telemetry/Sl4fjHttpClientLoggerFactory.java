package ru.tinkoff.kora.http.client.common.telemetry;

import org.slf4j.LoggerFactory;

public class Sl4fjHttpClientLoggerFactory implements HttpClientLoggerFactory {
    @Override
    public HttpClientLogger get(String clientName) {
        var requestLog = LoggerFactory.getLogger(clientName + ".request");
        var responseLog = LoggerFactory.getLogger(clientName + ".response");
        return new Sl4fjHttpClientLogger(requestLog, responseLog);
    }
}
