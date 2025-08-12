package ru.tinkoff.kora.micrometer.module.grpc.client;

import io.grpc.*;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetrics;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MetricsKey;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MicrometerGrpcClientTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public final class Opentelemetry120GrpcClientMetrics implements GrpcClientMetrics {

    record Metrics(DistributionSummary duration, DistributionSummary requestsByRpc, DistributionSummary responsesByRpc) {}

    private final ConcurrentHashMap<MetricsKey, Metrics> metrics = new ConcurrentHashMap<>();

    private final MeterRegistry registry;
    private final ServiceDescriptor service;
    private final TelemetryConfig.MetricsConfig config;
    private final URI uri;
    private final MicrometerGrpcClientTagsProvider tagsProvider;

    private static final AtomicIntegerFieldUpdater<Opentelemetry120GrpcClientMetrics> REQUESTS_PER_PRC = AtomicIntegerFieldUpdater.newUpdater(Opentelemetry120GrpcClientMetrics.class, "requestsPerRpc");
    private volatile int requestsPerRpc = 0;
    private static final AtomicIntegerFieldUpdater<Opentelemetry120GrpcClientMetrics> RESPONSES_PER_RPC = AtomicIntegerFieldUpdater.newUpdater(Opentelemetry120GrpcClientMetrics.class, "responsesPerRpc");
    private volatile int responsesPerRpc = 0;

    public Opentelemetry120GrpcClientMetrics(MeterRegistry registry,
                                             ServiceDescriptor service,
                                             TelemetryConfig.MetricsConfig config,
                                             URI uri,
                                             MicrometerGrpcClientTagsProvider tagsProvider) {
        this.registry = registry;
        this.service = service;
        this.config = config;
        this.uri = uri;
        this.tagsProvider = tagsProvider;
    }

    @Override
    public <RespT, ReqT> void recordEnd(MethodDescriptor<ReqT, RespT> method, long startTime, Exception e) {
        final Integer code;
        final Metadata metadata;
        if (e instanceof StatusException se) {
            code = se.getStatus().getCode().value();
            metadata = se.getTrailers();
        } else if (e instanceof StatusRuntimeException sre) {
            code = sre.getStatus().getCode().value();
            metadata = sre.getTrailers();
        } else {
            code = null;
            metadata = null;
        }

        var key = new MetricsKey(this.service.getName(), method.getBareMethodName(), code, e.getClass());
        var metrics = this.metrics.computeIfAbsent(key, k -> buildMetrics(k, metadata));
        var processingTime = ((double) (System.nanoTime() - startTime) / 1_000_000);

        metrics.duration.record(processingTime);
    }

    @Override
    public <RespT, ReqT> void recordEnd(MethodDescriptor<ReqT, RespT> method, long startTime, Status status, Metadata trailers) {
        var code = (status == null) ? null : status.getCode().value();
        var key = new MetricsKey(this.service.getName(), method.getBareMethodName(), code, null);
        var metrics = this.metrics.computeIfAbsent(key, k -> buildMetrics(k, trailers));
        var processingTime = ((double) (System.nanoTime() - startTime) / 1_000_000);

        metrics.duration.record(processingTime);
        metrics.requestsByRpc.record(requestsPerRpc);
        metrics.responsesByRpc.record(responsesPerRpc);
    }

    @Override
    public <RespT, ReqT> void recordSendMessage(MethodDescriptor<ReqT, RespT> method, ReqT message) {
        REQUESTS_PER_PRC.incrementAndGet(this);
    }

    @Override
    public <RespT, ReqT> void recordReceiveMessage(MethodDescriptor<ReqT, RespT> method, RespT message) {
        RESPONSES_PER_RPC.incrementAndGet(this);
    }

    /**
     * @see <a href="https://opentelemetry.io/docs/specs/semconv/rpc/rpc-metrics/#rpc-client">rpc-client</a>
     */
    private Metrics buildMetrics(MetricsKey method, @Nullable Metadata metadata) {
        var tags = tagsProvider.getTags(this.uri, method, metadata);

        var duration = DistributionSummary.builder("rpc.client.duration")
            .tags(tags)
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .register(this.registry);

        var requestsByRpc = DistributionSummary.builder("rpc.client.requests_per_rpc")
            .tags(tags)
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .register(this.registry);

        var responsesByRpc = DistributionSummary.builder("rpc.client.responses_per_rpc")
            .tags(tags)
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .register(this.registry);

        return new Metrics(duration, requestsByRpc, responsesByRpc);
    }
}
