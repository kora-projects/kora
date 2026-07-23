package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface HttpServerLoggerConfig extends TelemetryConfig.LogConfig {

    /**
     * @return Enables call stack logging on exception.
     */
    default boolean stacktrace() {
        return true;
    }

    /**
     * @return Request query parameters to hide.
     */
    default Set<String> maskQueries() {
        return Collections.emptySet();
    }

    /**
     * @return Request or response headers to hide.
     */
    default Set<String> maskHeaders() {
        return Set.of("authorization", "set-cookie", "cookie");
    }

    /**
     * @return Mask used to hide specified headers and request or response parameters.
     */
    default String mask() {
        return "***";
    }

    /**
     * @return Whether to use the request path template in logs, when not specified the template is used everywhere except TRACE level where the full path is used.
     */
    @Nullable
    Boolean pathTemplate();
}
