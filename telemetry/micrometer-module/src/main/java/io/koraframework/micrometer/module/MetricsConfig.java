package io.koraframework.micrometer.module;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.util.Map;

@ConfigMapper
public interface MetricsConfig {

    default boolean enabled() {
        return true;
    }

    default Map<String, String> tags() {
        return Map.of();
    }
}
