package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DefaultGrpcClientMetricsFactory {

    public static final DefaultGrpcClientMetricsFactory INSTANCE = new DefaultGrpcClientMetricsFactory();

    public DefaultGrpcClientMetrics create(DefaultGrpcClientTelemetry.TelemetryContext context) {
        return new DefaultGrpcClientMetrics(context);
    }

    public static class DefaultGrpcClientMetrics {

        public record DurationKey(String method,
                                  String service,
                                  String serverAddress,
                                  int serverPort,
                                  Status.Code statusCode,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(method, service, serverAddress, serverPort, statusCode, errorType, tags);
            }
        }

        protected final ConcurrentMap<DurationKey, Timer> durationCache = new ConcurrentHashMap<>();

        protected final DefaultGrpcClientTelemetry.TelemetryContext context;

        public DefaultGrpcClientMetrics(DefaultGrpcClientTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void record(MethodDescriptor<?, ?> method, @Nullable Status status, @Nullable Throwable error, long processingTimeNanos) {
            var key = createDurationKey(method, status, error);
            var meter = this.durationCache.computeIfAbsent(key, _ -> createDuration(key).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createDurationKey(MethodDescriptor<?, ?> method, @Nullable Status status, @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var statusCode = status == null ? Status.Code.UNKNOWN : status.getCode();
            var errorType = error == null ? null : error.getClass();
            var serverPort = this.context.uri().getPort();
            if (serverPort == -1) {
                serverPort = 80;
            }
            return new DurationKey(
                Objects.requireNonNullElse(method.getBareMethodName(), ""),
                Objects.requireNonNullElse(this.context.service().getName(), "GrpcService"),
                this.context.uri().getHost(),
                serverPort,
                statusCode,
                errorType,
                null
            );
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();
            var tags = new ArrayList<Tag>(7 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), metricKey.service()));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), metricKey.method()));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), Integer.toString(metricKey.statusCode().value())));
            tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), metricKey.serverAddress()));
            tags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(metricKey.serverPort())));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            for (var entry : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(entry.getKey(), entry.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }
            return Timer.builder("rpc.client.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(tags));
        }
    }
}
