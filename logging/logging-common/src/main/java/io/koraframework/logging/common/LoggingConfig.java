package io.koraframework.logging.common;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.util.Map;

@ConfigValueExtractor
public interface LoggingConfig {

    default Map<String, String> levels() {
        return Map.of();
    }
}
