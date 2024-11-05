package ru.tinkoff.kora.opentelemetry.module.soap.client;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTracer;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class OpentelemetrySoapClientTracerFactory implements SoapClientTracerFactory {

    private final Tracer tracer;

    public OpentelemetrySoapClientTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public SoapClientTracer get(TelemetryConfig.TracingConfig config, String serviceName, String soapMethod, String url) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetrySoapClientTracer(this.tracer, serviceName, serviceName, url);
        } else {
            return null;
        }
    }
}
