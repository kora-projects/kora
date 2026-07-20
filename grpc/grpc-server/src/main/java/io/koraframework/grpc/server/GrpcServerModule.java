package io.koraframework.grpc.server;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryFactory;
import io.koraframework.grpc.server.telemetry.impl.DefaultGrpcServerBodyConverter;
import io.koraframework.grpc.server.telemetry.impl.DefaultGrpcServerLoggerFactory;
import io.koraframework.grpc.server.telemetry.impl.DefaultGrpcServerMetricsFactory;
import io.koraframework.grpc.server.telemetry.impl.DefaultGrpcServerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface GrpcServerModule {

    @FactoryModule
    default GrpcServerFactoryModule grpcServer() {
        return new GrpcServerFactoryModule("kora-grpc", "grpcServer");
    }

    @DefaultComponent
    default GrpcServerTelemetryFactory defaultGrpcServerTelemetryFactory(@Nullable Tracer tracer,
                                                                         @Nullable MeterRegistry meterRegistry,
                                                                         @Nullable DefaultGrpcServerLoggerFactory loggerFactory,
                                                                         @Nullable DefaultGrpcServerMetricsFactory metricsFactory,
                                                                         @Nullable DefaultGrpcServerBodyConverter bodyConverter) {
        return new DefaultGrpcServerTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory, bodyConverter);
    }
}
