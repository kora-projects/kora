package io.koraframework.http.server.common;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface HttpServerConfig {

    default int port() {
        return 8080;
    }

    default boolean ignoreTrailingSlash() {
        return false;
    }

    default Duration socketReadTimeout() {
        return Duration.ZERO;
    }

    default Duration socketWriteTimeout() {
        return Duration.ZERO;
    }

    default boolean socketKeepAliveEnabled() {
        return false;
    }

    default boolean headerKeepAliveEnabled() {
        return false;
    }

    default boolean headerServerDateEnabled() {
        return true;
    }

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    default Size maxRequestBodySize() {
        return Size.of(256, Size.Type.MiB);
    }

    HttpServerTelemetryConfig telemetry();

    HttpServerCorsConfig cors();

    @ConfigValueExtractor
    interface HttpServerCorsConfig {

        default boolean enabled() {
            return false;
        }

        @Nullable
        default String allowOrigin() {
            return null;
        }

        default List<String> allowHeaders() {
            return List.of("*");
        }

        default List<String> allowMethods() {
            return List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
        }

        default boolean allowCredentials() {
            return true;
        }

        default List<String> exposeHeaders() {
            return List.of();
        }

        default Duration maxAge() {
            return Duration.ofHours(1);
        }
    }
}
