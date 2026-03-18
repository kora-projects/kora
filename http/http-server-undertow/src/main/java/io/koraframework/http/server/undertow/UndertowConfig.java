package io.koraframework.http.server.undertow;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface UndertowConfig {

    default int ioThreads() {
        return Math.max(Runtime.getRuntime().availableProcessors(), 2);
    }

    default Duration threadKeepAliveTimeout() {
        return Duration.ofSeconds(60);
    }
}
