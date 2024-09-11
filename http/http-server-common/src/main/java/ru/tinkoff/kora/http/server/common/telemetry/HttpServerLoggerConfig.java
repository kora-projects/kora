package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface HttpServerLoggerConfig extends TelemetryConfig.LogConfig {

    Set<String> DEFAULT_MASK_QUERIES = Collections.emptySet();
    Set<String> DEFAULT_MASK_HEADERS = Collections.singleton("authorization");

    default boolean stacktrace() {
        return true;
    }

    default Set<String> maskQueries() {
        return DEFAULT_MASK_QUERIES;
    }

    default Set<String> maskHeaders() {
        return DEFAULT_MASK_HEADERS;
    }
}
