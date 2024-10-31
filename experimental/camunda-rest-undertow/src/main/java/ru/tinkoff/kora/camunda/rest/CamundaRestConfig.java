package ru.tinkoff.kora.camunda.rest;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus.Experimental;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Experimental
@ConfigValueExtractor
public interface CamundaRestConfig {

    default boolean enabled() {
        return false;
    }

    default String path() {
        return "/engine-rest";
    }

    default Integer port() {
        return 8081;
    }

    default Duration shutdownWait() {
        return Duration.ofMillis(100);
    }

    CamundaOpenApiConfig openapi();

    CamundaRestTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface CamundaRestTelemetryConfig extends TelemetryConfig {

        @Override
        CamundaRestLoggerConfig logging();
    }

    @ConfigValueExtractor
    interface CamundaRestLoggerConfig extends TelemetryConfig.LogConfig {

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

    @ConfigValueExtractor
    interface CamundaOpenApiConfig {

        default List<String> file() {
            return List.of("openapi.json");
        }

        default boolean enabled() {
            return false;
        }

        default String endpoint() {
            return "/openapi";
        }

        SwaggerUIConfig swaggerui();

        RapidocConfig rapidoc();

        @ConfigValueExtractor
        interface SwaggerUIConfig {

            default boolean enabled() {
                return false;
            }

            default String endpoint() {
                return "/swagger-ui";
            }
        }

        @ConfigValueExtractor
        interface RapidocConfig {

            default boolean enabled() {
                return false;
            }

            default String endpoint() {
                return "/rapidoc";
            }
        }
    }
}
