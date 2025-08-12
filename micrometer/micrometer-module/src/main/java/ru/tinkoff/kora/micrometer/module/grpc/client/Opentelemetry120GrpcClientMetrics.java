package ru.tinkoff.kora.micrometer.module.grpc.client;

import io.grpc.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetrics;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MessageReceivedKey;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MessageSendKey;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MethodDurationKey;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MicrometerGrpcClientTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

public final class Opentelemetry120GrpcClientMetrics implements GrpcClientMetrics {

    private final ConcurrentHashMap<MethodDurationKey, DistributionSummary> durationMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MessageSendKey, Counter> requestsByRpcMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MessageReceivedKey, Counter> responsesByRpcMetrics = new ConcurrentHashMap<>();

    private final MeterRegistry registry;
    private final ServiceDescriptor service;
    private final TelemetryConfig.MetricsConfig config;
    private final URI uri;
    private final MicrometerGrpcClientTagsProvider tagsProvider;

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

        var key = new MethodDurationKey(this.service.getName(), method.getBareMethodName(), code, e.getClass());
        var metrics = this.durationMetrics.computeIfAbsent(key, k -> buildDurationMetrics(k, metadata));

        var processingTime = ((double) (System.nanoTime() - startTime) / 1_000_000);
        metrics.record(processingTime);
    }

    @Override
    public <RespT, ReqT> void recordEnd(MethodDescriptor<ReqT, RespT> method, long startTime, Status status, Metadata trailers) {
        var code = (status == null) ? null : status.getCode().value();

        var key = new MethodDurationKey(this.service.getName(), method.getBareMethodName(), code, null);
        var metrics = this.durationMetrics.computeIfAbsent(key, k -> buildDurationMetrics(k, trailers));

        var processingTime = ((double) (System.nanoTime() - startTime) / 1_000_000);
        metrics.record(processingTime);
    }

    @Override
    public <RespT, ReqT> void recordSendMessage(MethodDescriptor<ReqT, RespT> method, ReqT message) {
        var key = new MessageSendKey(this.service.getName(), method.getBareMethodName());
        var metrics = this.requestsByRpcMetrics.computeIfAbsent(key, k -> buildSendMetrics(k, message));
        metrics.increment();
    }

    @Override
    public <RespT, ReqT> void recordReceiveMessage(MethodDescriptor<ReqT, RespT> method, RespT message) {
        var key = new MessageReceivedKey(this.service.getName(), method.getBareMethodName());
        var metrics = this.responsesByRpcMetrics.computeIfAbsent(key, k -> buildReceivedMetrics(k, message));
        metrics.increment();
    }

    /**
     * @see <a href="https://opentelemetry.io/docs/specs/semconv/rpc/rpc-metrics/#rpc-client">rpc-client</a>
     */
    private DistributionSummary buildDurationMetrics(MethodDurationKey method, @Nullable Metadata metadata) {
        var duration = DistributionSummary.builder("rpc.client.duration")
            .tags(tagsProvider.getMethodDurationTags(this.uri, method, metadata))
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .register(this.registry);

        return duration;
    }

    private Counter buildSendMetrics(MessageSendKey key, Object request) {
        var requestsByRpc = Counter.builder("rpc.client.requests_per_rpc")
            .tags(tagsProvider.getMessageSendTags(this.uri, key, request))
            .register(this.registry);

        return requestsByRpc;
    }

    private Counter buildReceivedMetrics(MessageReceivedKey key, Object response) {
        var responsesByRpc = Counter.builder("rpc.client.responses_per_rpc")
            .tags(tagsProvider.getMessageReceivedTags(this.uri, key, response))
            .register(this.registry);

        return responsesByRpc;
    }
}
