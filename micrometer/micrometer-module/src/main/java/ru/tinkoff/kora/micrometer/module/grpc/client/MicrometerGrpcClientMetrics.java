package ru.tinkoff.kora.micrometer.module.grpc.client;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public final class MicrometerGrpcClientMetrics implements GrpcClientMetrics {
    private final ConcurrentHashMap<MetricsKey, Metrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry registry;
    private final ServiceDescriptor service;
    private final TelemetryConfig.MetricsConfig config;
    private final URI uri;

    private static final AtomicIntegerFieldUpdater<MicrometerGrpcClientMetrics> REQUESTS_PER_PRC = AtomicIntegerFieldUpdater.newUpdater(MicrometerGrpcClientMetrics.class, "requestsPerRpc");
    private volatile int requestsPerRpc = 0;
    private static final AtomicIntegerFieldUpdater<MicrometerGrpcClientMetrics> RESPONSES_PER_RPC = AtomicIntegerFieldUpdater.newUpdater(MicrometerGrpcClientMetrics.class, "responsesPerRpc");
    private volatile int responsesPerRpc = 0;

    public MicrometerGrpcClientMetrics(MeterRegistry registry, ServiceDescriptor service, TelemetryConfig.MetricsConfig config, URI uri) {
        this.registry = registry;
        this.service = service;
        this.config = config;
        this.uri = uri;
    }

    record MetricsKey(String serviceName, String fullMetricsKey) {}

    record Metrics(DistributionSummary duration, DistributionSummary requestsByRpc, DistributionSummary responsesByRpc) {}

    @Override
    public <RespT, ReqT> void recordEnd(MethodDescriptor<ReqT, RespT> method, long startTime, Exception e) {
        var key = new MetricsKey(this.service.getName(), method.getFullMethodName());
        var metrics = this.metrics.computeIfAbsent(key, k -> this.buildMetrics(method));
        var processingTime = ((double) (System.nanoTime() - startTime) / 1_000_000);

        metrics.duration.record(processingTime);
    }

    /**
     * @see <a href="https://opentelemetry.io/docs/specs/semconv/rpc/rpc-metrics/#rpc-client">rpc-client</a>
     */
    private <RespT, ReqT> Metrics buildMetrics(MethodDescriptor<ReqT, RespT> method) {
        var rpcMethod = method.getBareMethodName();
        var tags = tags(method, rpcMethod);


        var duration = DistributionSummary.builder("rpc.client.duration")
            .tags(tags)
            .serviceLevelObjectives(this.config.slo())
            .register(this.registry);
        var requestsByRpc = DistributionSummary.builder("rpc.client.requests_per_rpc")
            .tags(tags)
            .serviceLevelObjectives(this.config.slo())
            .register(this.registry);
        var responsesByRpc = DistributionSummary.builder("rpc.client.responses_per_rpc")
            .tags(tags)
            .serviceLevelObjectives(this.config.slo())
            .register(this.registry);

        return new Metrics(duration, requestsByRpc, responsesByRpc);
    }

    private <RespT, ReqT> List<Tag> tags(MethodDescriptor<ReqT, RespT> method, String rpcMethod) {
        var rpcService = Objects.requireNonNullElse(method.getServiceName(), "GrpcService");
        var serverAddress = this.uri.getHost();
        var serverPort = this.uri.getPort();
        if (serverPort == -1) {
            serverPort = 80;
        }

        return List.of(
            Tag.of(SemanticAttributes.RPC_METHOD.getKey(), rpcMethod),
            Tag.of(SemanticAttributes.RPC_SERVICE.getKey(), rpcService),
            Tag.of(SemanticAttributes.RPC_SYSTEM.getKey(), "grpc"),
            Tag.of("server.address", serverAddress),
            Tag.of("server.port", String.valueOf(serverPort))
        );
    }

    @Override
    public <RespT, ReqT> void recordEnd(MethodDescriptor<ReqT, RespT> method, long startTime, Status status, Metadata trailers) {
        var key = new MetricsKey(this.service.getName(), method.getFullMethodName());
        var metrics = this.metrics.computeIfAbsent(key, k -> this.buildMetrics(method));
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
}
