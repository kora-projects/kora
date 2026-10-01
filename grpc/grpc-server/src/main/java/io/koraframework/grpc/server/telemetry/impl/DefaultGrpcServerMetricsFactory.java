package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Status;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DefaultGrpcServerMetricsFactory {

    public static final DefaultGrpcServerMetricsFactory INSTANCE = new DefaultGrpcServerMetricsFactory();

    public DefaultGrpcServerMetrics create(DefaultGrpcServerTelemetry.TelemetryContext context) {
        return new DefaultGrpcServerMetrics(context);
    }

    public static class DefaultGrpcServerMetrics {

        public record DurationKey(String service,
                                  String method,
                                  Status.Code statusCode,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(service, method, statusCode, errorType, tags);
            }
        }

        protected final ConcurrentMap<DurationKey, Timer> durationCache = new ConcurrentHashMap<>();

        protected final DefaultGrpcServerTelemetry.TelemetryContext context;

        public DefaultGrpcServerMetrics(DefaultGrpcServerTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void record(String service, String method, @Nullable Status status, @Nullable Throwable error, long processingTimeNanos) {
            var key = createDurationKey(service, method, status, error);
            var meter = this.durationCache.computeIfAbsent(key, _ -> createDuration(key).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createDurationKey(String service, String method, @Nullable Status status, @Nullable Throwable error) {
            var errorType = error == null ? null : error.getClass();
            var statusCode = status == null ? Status.Code.UNKNOWN : status.getCode();
            return new DurationKey(service, method, statusCode, errorType, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        @SuppressWarnings("deprecation")
        protected Timer.Builder createDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : Objects.requireNonNullElseGet(metricKey.errorType.getCanonicalName(), metricKey.errorType::getName);
            var tags = new ArrayList<Tag>(7 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of("server.name", this.context.name()));
            tags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(this.context.port())));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM_NAME.getKey(), RpcIncubatingAttributes.RpcSystemNameIncubatingValues.GRPC));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), metricKey.service()));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), metricKey.method()));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE.getKey(), metricKey.statusCode().name()));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            for (var entry : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(entry.getKey(), entry.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }
            return Timer.builder("rpc.server.call.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(Tags.of(tags));
        }
    }
}
