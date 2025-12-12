package ru.tinkoff.kora.s3.client.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import ru.tinkoff.kora.s3.client.S3ClientConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultS3ClientTelemetry implements S3ClientTelemetry {
    protected final S3ClientConfig config;
    protected final Tracer tracer;
    protected final MeterRegistry meterRegistry;
    protected final ConcurrentMap<Tags, Timer> durationCache = new ConcurrentHashMap<>();

    public DefaultS3ClientTelemetry(S3ClientConfig config, Tracer tracer, MeterRegistry meterRegistry) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public S3ClientObservation observe(String operation, String bucket) {
        var span = this.createSpan(operation, bucket);
        var duration = this.createDuration(operation, bucket);

        return new DefaultS3ClientObservation(span, duration);
    }

    protected Meter.MeterProvider<Timer> createDuration(String operation, String bucket) {
        return tags -> durationCache.computeIfAbsent(Tags.of(tags), t -> {
            var builder = Timer.builder("rpc.client.duration")
                .serviceLevelObjectives(this.config.telemetry().metrics().slo())
                .tag(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), "raws-api")
                .tag(RpcIncubatingAttributes.RPC_METHOD.getKey(), operation)
                .tag(AwsIncubatingAttributes.AWS_S3_BUCKET.getKey(), bucket)
                .tags(t);
            for (var tag : this.config.telemetry().metrics().tags().entrySet()) {
                builder.tag(tag.getValue(), tag.getValue());
            }

            return builder.register(this.meterRegistry);
        });
    }

    protected Span createSpan(String operation, String bucket) {
        if (!this.config.telemetry().tracing().enabled()) {
            return Span.getInvalid();
        }
        var span = this.tracer.spanBuilder("S3." + operation)
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "aws-api")
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, operation)
            .setAttribute(AwsIncubatingAttributes.AWS_S3_BUCKET, bucket);
        for (var entry : this.config.telemetry().tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span.startSpan();
    }
}
