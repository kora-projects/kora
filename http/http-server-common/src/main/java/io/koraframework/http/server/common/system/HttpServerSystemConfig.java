package io.koraframework.http.server.common.system;

import io.koraframework.config.common.annotation.ConfigMapper;

@ConfigMapper
public interface HttpServerSystemConfig {

    default String metricsPath() {
        return "/metrics";
    }

    default String readinessPath() {
        return "/system/readiness";
    }

    default String livenessPath() {
        return "/system/liveness";
    }
}
