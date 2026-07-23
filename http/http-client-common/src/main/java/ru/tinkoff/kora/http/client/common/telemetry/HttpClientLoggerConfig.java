package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface HttpClientLoggerConfig extends TelemetryConfig.LogConfig {

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
     * @return Whether to use the request path template in logging, when not specified the template is used except at TRACE level where the full path is used.
     */
    @Nullable
    Boolean pathTemplate();
}
