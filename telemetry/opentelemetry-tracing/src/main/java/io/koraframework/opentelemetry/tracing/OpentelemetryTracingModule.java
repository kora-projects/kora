package io.koraframework.opentelemetry.tracing;

import io.koraframework.application.graph.LifecycleWrapper;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public interface OpentelemetryTracingModule {

    default OpentelemetryTracingConfig opentelemetryResourceConfig(Config config, ConfigValueMapper<OpentelemetryTracingConfig> mapper) {
        return mapper.mapOrThrow(config.get("tracing"));
    }

    default Resource opentelemetryTracingResource(OpentelemetryTracingConfig config) {
        var resource = Resource.builder();
        for (var attribute : config.attributes().entrySet()) {
            resource.put(attribute.getKey(), attribute.getValue());
        }
        return resource.build();
    }

    @DefaultComponent
    default IdGenerator opentelemetryTracingIdGenerator() {
        return IdGenerator.random();
    }

    @DefaultComponent
    default Supplier<SpanLimits> opentelemetryTracingSpanLimitsSupplier() {
        return SpanLimits::getDefault;
    }

    @DefaultComponent
    default Sampler opentelemetryTracingSampler() {
        return Sampler.parentBased(Sampler.alwaysOn());
    }

    default LifecycleWrapper<TracerProvider> opentelemetryTracerProvider(IdGenerator idGenerator,
                                                                         Supplier<SpanLimits> spanLimits,
                                                                         Sampler sampler,
                                                                         @Nullable SpanProcessor spanProcessor,
                                                                         Resource resource,
                                                                         OpentelemetryTracingConfig tracingConfig) {
        if (!tracingConfig.enabled()) {
            return new LifecycleWrapper<>(
                TracerProvider.noop(),
                p -> {},
                p -> {}
            );
        }

        if (spanProcessor == null) {
            spanProcessor = SpanProcessor.composite();
        }

        return new LifecycleWrapper<>(
            SdkTracerProvider.builder()
                .setIdGenerator(idGenerator)
                .setSpanLimits(spanLimits)
                .setSampler(sampler)
                .addSpanProcessor(spanProcessor)
                .setResource(resource)
                .build(),
            p -> {},
            p -> ((SdkTracerProvider) p).close()
        );
    }

    default Tracer opentelemetryTracer(TracerProvider tracerProvider) {
        return tracerProvider
            .tracerBuilder("kora")
            .build();
    }

    @DefaultComponent
    default KoraTracer koraTracer(Tracer tracer) {
        return new KoraTracer(tracer);
    }
}
