package io.koraframework.opentelemetry.tracing;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.util.Map;

@ConfigValueExtractor
public interface OpentelemetryResourceConfig {
    default Map<String, String> attributes() {
        return Map.of();
    }
}
