package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface HttpServerLoggerConfig extends TelemetryConfig.LogConfig {

    default boolean stacktrace() {
        return true;
    }

    default Set<String> maskQueries() {
        return Collections.emptySet();
    }

    default Set<String> maskHeaders() {
        return Set.of("authorization");
    }

    default String mask() {
        return "***";
    }

    @Nullable
    Boolean pathTemplate();
}
