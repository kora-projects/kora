package ru.tinkoff.kora.camunda.rest;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;

import java.time.Duration;
import java.util.List;

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
        return Duration.ofSeconds(30);
    }

    CamundaOpenApiConfig openapi();

    HttpServerTelemetryConfig telemetry();

    CamundaCorsConfig cors();

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

    @ConfigValueExtractor
    interface CamundaCorsConfig {

        default boolean enabled() {
            return false;
        }

        @Nullable
        String allowOrigin();

        default List<String> allowHeaders() {
            return List.of("*");
        }

        default List<String> allowMethods() {
            return List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
        }

        default Boolean allowCredentials() {
            return true;
        }

        default List<String> exposeHeaders() {
            return List.of("*");
        }

        default Duration maxAge() {
            return Duration.ofHours(1);
        }
    }
}
