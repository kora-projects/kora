package ru.tinkoff.grpc.client.telemetry;

import io.grpc.ServiceDescriptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;

public final class DefaultGrpcClientTelemetryFactory implements GrpcClientTelemetryFactory {
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public DefaultGrpcClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        this.tracer = Objects.requireNonNullElseGet(tracer, () -> TracerProvider.noop().get("grpc-client"));
        this.meterRegistry = Objects.requireNonNullElseGet(meterRegistry, CompositeMeterRegistry::new);
    }

    @Override
    public GrpcClientTelemetry get(ServiceDescriptor service, GrpcClientTelemetryConfig telemetryConfig, URI uri) {
        if (!telemetryConfig.logging().enabled() && !telemetryConfig.tracing().enabled() && !telemetryConfig.metrics().enabled()) {
            return NoopGrpcClientTelemetry.INSTANCE;
        }
        var meterRegistry = telemetryConfig.metrics().enabled()
            ? this.meterRegistry
            : new CompositeMeterRegistry();
        var tracer = telemetryConfig.tracing().enabled()
            ? this.tracer
            : TracerProvider.noop().get("grpc-client");
        return new DefaultGrpcClientTelemetry(telemetryConfig, tracer, meterRegistry, service, uri);
    }
}
