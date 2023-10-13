package ru.tinkoff.kora.micrometer.module.grpc.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.grpc.telemetry.GrpcServerMetrics;
import ru.tinkoff.kora.grpc.telemetry.GrpcServerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerGrpcServerMetricsFactory implements GrpcServerMetricsFactory {
    private final ConcurrentHashMap<MetricsKey, GrpcServerMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public MicrometerGrpcServerMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private record MetricsKey(String serviceName, String methodName) {}

    @Override
    public GrpcServerMetrics get(TelemetryConfig.MetricsConfig config, ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {
        return this.metrics.computeIfAbsent(new MetricsKey(serviceName, methodName), key -> buildMetrics(config, key));
    }

    private GrpcServerMetrics buildMetrics(TelemetryConfig.MetricsConfig config, MetricsKey metricsKey) {
        var duration = DistributionSummary.builder("rpc.server.duration")
            .serviceLevelObjectives(config.slo())
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
