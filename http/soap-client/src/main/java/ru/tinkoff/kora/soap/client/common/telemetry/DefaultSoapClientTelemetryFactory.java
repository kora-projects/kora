package ru.tinkoff.kora.soap.client.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;
import ru.tinkoff.kora.soap.client.common.SoapMethodDescriptor;
import ru.tinkoff.kora.soap.client.common.SoapServiceConfig;

public class DefaultSoapClientTelemetryFactory implements SoapClientTelemetryFactory {

    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final Tracer tracer;

    public DefaultSoapClientTelemetryFactory(
        @Nullable MeterRegistry meterRegistry,
        @Nullable Tracer tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    public SoapClientTelemetry get(SoapServiceConfig.SoapClientTelemetryConfig config, SoapMethodDescriptor descriptor, String url) {
        if (!config.metrics().enabled() && !config.tracing().enabled() && !config.logging().enabled()) {
            return NoopSoapClientTelemetry.INSTANCE;
        }

        var meterRegistry = this.meterRegistry;
        if (meterRegistry == null || !config.metrics().enabled()) {
            meterRegistry = new CompositeMeterRegistry();
        }
        var tracer = this.tracer;
        if (tracer == null || !config.tracing().enabled()) {
            tracer = TracerProvider.noop().get("soap-client-telemetry");
        }
        var loggerFactory = config.logging().enabled()
            ? LoggerFactory.getILoggerFactory()
            : new NOPLoggerFactory();
        return new DefaultSoapClientTelemetry(config, tracer, meterRegistry, loggerFactory, descriptor, url);
    }
}
