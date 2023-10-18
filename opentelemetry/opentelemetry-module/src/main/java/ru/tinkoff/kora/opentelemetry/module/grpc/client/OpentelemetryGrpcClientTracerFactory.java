package ru.tinkoff.kora.opentelemetry.module.grpc.client;

import io.grpc.ServiceDescriptor;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTracer;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;

public final class OpentelemetryGrpcClientTracerFactory implements GrpcClientTracerFactory {
    private final Tracer tracer;

    public OpentelemetryGrpcClientTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public GrpcClientTracer get(ServiceDescriptor service, TelemetryConfig.TracingConfig config, URI uri) {
        if (config.enabled() != null && !config.enabled()) {
            return null;
        }
        return new OpentelemetryGrpcClientTracer(this.tracer, service, uri);
    }
}
