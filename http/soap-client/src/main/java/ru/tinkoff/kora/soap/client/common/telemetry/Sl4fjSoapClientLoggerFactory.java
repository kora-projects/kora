package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class Sl4fjSoapClientLoggerFactory implements SoapClientLoggerFactory {

    private final SoapClientLogger.SoapClientLoggerBodyMapper mapper;

    public Sl4fjSoapClientLoggerFactory(SoapClientLogger.SoapClientLoggerBodyMapper mapper) {
        this.mapper = mapper;
    }

    @Nullable
    @Override
    public SoapClientLogger get(TelemetryConfig.LogConfig logging, String serviceClass, String serviceName, String soapMethod, String url) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            var requestLog = LoggerFactory.getLogger(serviceClass + ".request");
            var responseLog = LoggerFactory.getLogger(serviceClass + ".response");
            return new Sl4fjSoapClientLogger(mapper, requestLog, responseLog, serviceName, soapMethod);
        } else {
            return null;
        }
    }
}
