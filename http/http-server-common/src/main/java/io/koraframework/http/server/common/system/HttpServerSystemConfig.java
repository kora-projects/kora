package io.koraframework.http.server.common.system;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.http.server.common.HttpServerConfig;

@ConfigValueExtractor
public interface HttpServerSystemConfig extends HttpServerConfig {

    default String metricsPath() {
        return "/metrics";
    }

    default String readinessPath() {
        return "/system/readiness";
    }

    default String livenessPath() {
        return "/system/liveness";
    }

    @Override
    default int port() {
        return 8085;
    }
}
