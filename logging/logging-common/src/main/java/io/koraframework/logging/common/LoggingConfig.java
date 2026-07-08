package io.koraframework.logging.common;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.util.Map;

@ConfigMapper
public interface LoggingConfig {

    default Map<String, String> levels() {
        return Map.of();
    }
}
