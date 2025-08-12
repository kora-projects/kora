package ru.tinkoff.kora.micrometer.module.grpc.client;

import io.grpc.*;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetrics;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MetricsKey;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MicrometerGrpcClientTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public final class Opentelemetry123GrpcClientMetrics implements GrpcClientMetrics {

    private final ConcurrentHashMap<MetricsKey, Metrics> metrics = new ConcurrentHashMap<>();

    private final MeterRegistry registry;
    private final ServiceDescriptor service;
    private final TelemetryConfig.MetricsConfig config;
    private final URI uri;
    private final MicrometerGrpcClientTagsProvider tagsProvider;

    private static final AtomicIntegerFieldUpdater<Opentelemetry123GrpcClientMetrics> REQUESTS_PER_PRC = AtomicIntegerFieldUpdater.newUpdater(Opentelemetry123GrpcClientMetrics.class, "requestsPerRpc");
    private volatile int requestsPerRpc = 0;
    private static final AtomicIntegerFieldUpdater<Opentelemetry123GrpcClientMetrics> RESPONSES_PER_RPC = AtomicIntegerFieldUpdater.newUpdater(Opentelemetry123GrpcClientMetrics.class, "responsesPerRpc");
    private volatile int responsesPerRpc = 0;

    public Opentelemetry123GrpcClientMetrics(MeterRegistry registry,
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

    record Metrics(DistributionSummary duration, DistributionSummary requestsByRpc, DistributionSummary responsesByRpc) {}

    @Override
    public <RespT, ReqT> void recordEnd(MethodDescriptor<ReqT, RespT> method, long startTime, Exception e) {
        Integer code = null;
        if (e instanceof StatusException se) {
            code = se.getStatus().getCode().value();
        } else if (e instanceof StatusRuntimeException sre) {
            code = sre.getStatus().getCode().value();
        }

        var key = new MetricsKey(this.service.getName(), method.getBareMethodName(), code, e.getClass());
        var metrics = this.metrics.computeIfAbsent(key, k -> buildMetrics(k, null));
        var processingTime = ((double) (System.nanoTime() - startTime) / 1_000_000_000);

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
    private Metrics buildMetrics(MetricsKey method, Metadata metadata) {
        var tags = tagsProvider.getTags(this.uri, method, metadata);

        var duration = DistributionSummary.builder("rpc.client.duration")
            .tags(tags)
            .baseUnit("s")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .register(this.registry);

        var requestsByRpc = DistributionSummary.builder("rpc.client.requests_per_rpc")
            .tags(tags)
            .baseUnit("s")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .register(this.registry);

        var responsesByRpc = DistributionSummary.builder("rpc.client.responses_per_rpc")
            .tags(tags)
            .baseUnit("s")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .register(this.registry);

        return new Metrics(duration, requestsByRpc, responsesByRpc);
    }
}
