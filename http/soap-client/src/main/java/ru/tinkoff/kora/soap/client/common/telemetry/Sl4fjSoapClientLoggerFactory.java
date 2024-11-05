package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class Sl4fjSoapClientLoggerFactory implements SoapClientLoggerFactory {

    @Nullable
    @Override
    public SoapClientLogger get(TelemetryConfig.LogConfig logging, String serviceName, String soapMethod, String url) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            var requestLog = LoggerFactory.getLogger(serviceName + ".request");
            var responseLog = LoggerFactory.getLogger(serviceName + ".response");
            return new Sl4fjSoapClientLogger(requestLog, responseLog, soapMethod, url);
        } else {
            return null;
        }
    }
}
