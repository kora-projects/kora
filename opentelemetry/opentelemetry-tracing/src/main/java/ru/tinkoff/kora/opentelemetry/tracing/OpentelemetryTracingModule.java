package ru.tinkoff.kora.opentelemetry.tracing;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.opentelemetry.module.OpentelemetryModule;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public interface OpentelemetryTracingModule extends OpentelemetryModule {

    default OpentelemetryResourceConfig opentelemetryResourceConfig(Config config, ConfigValueExtractor<OpentelemetryResourceConfig> extractor) {
        return extractor.extract(config.get("tracing"));
    }

    default Resource opentelemetryTracingResource(OpentelemetryResourceConfig config) {
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

    default LifecycleWrapper<SdkTracerProvider> opentelemetryTracerProvider(IdGenerator idGenerator, Supplier<SpanLimits> spanLimits, Sampler sampler, @Nullable SpanProcessor spanProcessor, Resource resource) {
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
            SdkTracerProvider::close
        );
    }

    default Tracer opentelemetryTracer(TracerProvider tracerProvider) {
        return tracerProvider
            .tracerBuilder("kora")
            .build();
    }
}
