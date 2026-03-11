package io.koraframework.http.server.common;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface PrivateHttpServerConfig extends HttpServerConfig {

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
