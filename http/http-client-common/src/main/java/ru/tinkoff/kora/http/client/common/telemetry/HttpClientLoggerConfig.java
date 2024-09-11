package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface HttpClientLoggerConfig extends TelemetryConfig.LogConfig {

    Set<String> DEFAULT_MASK_QUERIES = Collections.emptySet();
    Set<String> DEFAULT_MASK_HEADERS = Collections.singleton("authorization");

    default Set<String> maskQueries() {
        return DEFAULT_MASK_QUERIES;
    }

    default Set<String> maskHeaders() {
        return DEFAULT_MASK_HEADERS;
    }
}
