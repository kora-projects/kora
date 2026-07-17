package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Status;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
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
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(service, method, statusCode, tags);
            }
        }

        protected final ConcurrentMap<DurationKey, Timer> durationCache = new ConcurrentHashMap<>();

        protected final DefaultGrpcServerTelemetry.TelemetryContext context;

        public DefaultGrpcServerMetrics(DefaultGrpcServerTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void record(String service, String method, @Nullable Status status, long processingTimeNanos) {
            var key = createDurationKey(service, method, status);
            var meter = this.durationCache.computeIfAbsent(key, _ -> createDuration(key).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createDurationKey(String service, String method, @Nullable Status status) {
            var statusCode = status == null ? Status.Code.UNKNOWN : status.getCode();
            return new DurationKey(service, method, statusCode, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var tags = new ArrayList<Tag>(6 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of("server.name", this.context.name()));
            tags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(this.context.port())));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), metricKey.service()));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), metricKey.method()));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), Integer.toString(metricKey.statusCode().value())));
            for (var entry : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(entry.getKey(), entry.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }
            return Timer.builder("rpc.server.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(Tags.of(tags));
        }
    }
}
