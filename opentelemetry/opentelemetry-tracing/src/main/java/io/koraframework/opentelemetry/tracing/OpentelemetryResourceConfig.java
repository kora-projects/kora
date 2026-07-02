package io.koraframework.opentelemetry.tracing;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.util.Map;

@ConfigMapper
public interface OpentelemetryResourceConfig {
    default Map<String, String> attributes() {
        return Map.of();
    }
}
