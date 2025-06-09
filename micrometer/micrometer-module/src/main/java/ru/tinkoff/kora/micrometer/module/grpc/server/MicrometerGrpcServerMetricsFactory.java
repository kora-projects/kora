package ru.tinkoff.kora.micrometer.module.grpc.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerMetrics;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.micrometer.module.grpc.server.tag.MetricsKey;
import ru.tinkoff.kora.micrometer.module.grpc.server.tag.MicrometerGrpcServerTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class MicrometerGrpcServerMetricsFactory implements GrpcServerMetricsFactory {
    private final ConcurrentHashMap<MetricsKey, GrpcServerMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;
    private final MicrometerGrpcServerTagsProvider grpcServerTagsProvider;

    public MicrometerGrpcServerMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig, MicrometerGrpcServerTagsProvider grpcServerTagsProvider) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
        this.grpcServerTagsProvider = grpcServerTagsProvider;
    }

    @Override
    public GrpcServerMetrics get(TelemetryConfig.MetricsConfig config, ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {
        return this.metrics.computeIfAbsent(new MetricsKey(serviceName, methodName), key -> buildMetrics(config, key));
    }

    private GrpcServerMetrics buildMetrics(TelemetryConfig.MetricsConfig config, MetricsKey metricsKey) {
        var duration = (Function<Integer, DistributionSummary>) code -> DistributionSummary.builder("rpc.server.duration")
            .serviceLevelObjectives(config.slo(metricsConfig.opentelemetrySpec()))
            .baseUnit(switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> "milliseconds";
                case V123 -> "s";
            })
            .tags(this.grpcServerTagsProvider.getDurationTags(code, metricsKey))
            .register(this.meterRegistry);

        var requestsPerRpc = Counter.builder("rpc.server.requests_per_rpc")
            .baseUnit("messages")
            .tags(this.grpcServerTagsProvider.getRequestsTags(metricsKey))
            .register(this.meterRegistry);

        var responsesPerRpc = Counter.builder("rpc.server.responses_per_rpc")
            .baseUnit("messages")
            .tags(this.grpcServerTagsProvider.getResponsesTags(metricsKey))
            .register(this.meterRegistry);

        return switch (metricsConfig.opentelemetrySpec()) {
            case V120 -> new Opentelemetry120GrpcServerMetrics(duration, requestsPerRpc, responsesPerRpc);
            case V123 -> new Opentelemetry123GrpcServerMetrics(duration, requestsPerRpc, responsesPerRpc);
        };
    }
}
