package ru.tinkoff.kora.micrometer.module.grpc.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.SemanticAttributes;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerMetrics;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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
        var duration = (Function<Status, DistributionSummary>) status -> DistributionSummary.builder("rpc.server.duration")
            .serviceLevelObjectives(config.slo(null))
            .baseUnit(switch (config.spec()) {
                case V120 -> "milliseconds";
                case V123 -> "s";
            })
            .tag(SemanticAttributes.RPC_SYSTEM.getKey(), SemanticAttributes.RpcSystemValues.GRPC)
            .tag(SemanticAttributes.RPC_SERVICE.getKey(), metricsKey.serviceName)
            .tag(SemanticAttributes.RPC_METHOD.getKey(), metricsKey.methodName)
            .tag(SemanticAttributes.RPC_GRPC_STATUS_CODE.getKey(), Integer.toString(status.getCode().value()))
            .register(this.meterRegistry);
        var requestsPerRpc = Counter.builder("rpc.server.requests_per_rpc")
            .baseUnit("messages")
            .tag(SemanticAttributes.RPC_SYSTEM.getKey(), SemanticAttributes.RpcSystemValues.GRPC)
            .tag(SemanticAttributes.RPC_SERVICE.getKey(), metricsKey.serviceName)
            .tag(SemanticAttributes.RPC_METHOD.getKey(), metricsKey.methodName)
            .register(this.meterRegistry);
        var responsesPerRpc = Counter.builder("rpc.server.responses_per_rpc")
            .baseUnit("messages")
            .tag(SemanticAttributes.RPC_SYSTEM.getKey(), SemanticAttributes.RpcSystemValues.GRPC)
            .tag(SemanticAttributes.RPC_SERVICE.getKey(), metricsKey.serviceName)
            .tag(SemanticAttributes.RPC_METHOD.getKey(), metricsKey.methodName)
            .register(this.meterRegistry);
        return switch (config.spec()) {
            case V120 -> new Opentelemetry120GrpcServerMetrics(duration, requestsPerRpc, responsesPerRpc);
            case V123 -> new Opentelemetry123GrpcServerMetrics(duration, requestsPerRpc, responsesPerRpc);
        };
    }
}
