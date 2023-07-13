package ru.tinkoff.kora.micrometer.module.grpc.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.grpc.telemetry.GrpcServerMetrics;
import ru.tinkoff.kora.grpc.telemetry.GrpcServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.GrpcServerMetricsConfig;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerGrpcServerMetricsFactory implements GrpcServerMetricsFactory {
    private final ConcurrentHashMap<MetricsKey, GrpcServerMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final GrpcServerMetricsConfig config;

    public MicrometerGrpcServerMetricsFactory(MeterRegistry meterRegistry, @Nullable GrpcServerMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    private record MetricsKey(String serviceName, String methodName) {}

    @Override
    public GrpcServerMetrics get(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {
        return this.metrics.computeIfAbsent(new MetricsKey(serviceName, methodName), this::buildMetrics);
    }

    private GrpcServerMetrics buildMetrics(MetricsKey metricsKey) {
        var duration = DistributionSummary.builder("rpc.server.duration")
            .serviceLevelObjectives(this.config.slo())
            .baseUnit("milliseconds")
            .tag("rpc.system", "grpc")
            .tag("rpc.service", metricsKey.serviceName)
            .tag("rpc.method", metricsKey.methodName)
            .register(this.meterRegistry);
        var requestsPerRpc = Counter.builder("rpc.server.requests_per_rpc")
            .baseUnit("messages")
            .tag("rpc.system", "grpc")
            .tag("rpc.service", metricsKey.serviceName)
            .tag("rpc.method", metricsKey.methodName)
            .register(this.meterRegistry);
        var responsesPerRpc = Counter.builder("rpc.server.responses_per_rpc")
            .baseUnit("messages")
            .tag("rpc.system", "grpc")
            .tag("rpc.service", metricsKey.serviceName)
            .tag("rpc.method", metricsKey.methodName)
            .register(this.meterRegistry);
        return new MicrometerGrpcServerMetrics(duration, requestsPerRpc, responsesPerRpc);
    }
}
