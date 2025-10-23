package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface HttpClientLoggerConfig extends TelemetryConfig.LogConfig {

    default Set<String> maskQueries() {
        return Collections.emptySet();
    }

    default Set<String> maskHeaders() {
        return Set.of("authorization", "cookie");
    }

    default String mask() {
        return "***";
    }

    default boolean pathTemplate() {
        return false;
    }
}
