package ru.tinkoff.kora.opentelemetry.tracing;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.Map;

@ConfigValueExtractor
public interface OpentelemetryResourceConfig {
    default Map<String, String> attributes() {
        return Map.of();
    }
}
