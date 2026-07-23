package ru.tinkoff.kora.opentelemetry.tracing;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.Map;

@ConfigValueExtractor
public interface OpentelemetryResourceConfig {
    /**
     * @return OpenTelemetry Resource attributes added to every exported span.
     */
    default Map<String, String> attributes() {
        return Map.of();
    }
}
