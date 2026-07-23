package io.koraframework.opentelemetry.tracing;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.util.Map;

@ConfigMapper
public interface OpentelemetryTracingConfig {

    default boolean enabled() {
        return true;
    }

    default Map<String, String> attributes() {
        return Map.of();
    }
}
