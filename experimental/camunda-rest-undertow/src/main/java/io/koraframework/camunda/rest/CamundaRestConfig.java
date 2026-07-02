package io.koraframework.camunda.rest;

import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetryConfig;
import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigMapper
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

    CamundaRestTelemetryConfig telemetry();

    CamundaCorsConfig cors();

    @ConfigMapper
    interface CamundaOpenApiConfig {

        default List<String> files() {
            return List.of("openapi.json");
        }

        default boolean enabled() {
            return false;
        }

        default String path() {
            return "/openapi";
        }

        SwaggerUIConfig swaggerui();

        RapidocConfig rapidoc();

        @ConfigMapper
        interface SwaggerUIConfig {

            default boolean enabled() {
                return false;
            }

            default String path() {
                return "/swagger-ui";
            }

            default boolean withCredentials() {
                return true;
            }

            default Map<String, String> options() {
                return Map.of(
                    "layout", "StandaloneLayout",
                    "validatorUrl", "null",
                    "defaultModelsExpandDepth", "0",
                    "deepLinking", "true",
                    "persistAuthorization", "true",
                    "displayOperationId", "true",
                    "filter", "true"
                );
            }
        }

        @ConfigMapper
        interface RapidocConfig {

            default boolean enabled() {
                return false;
            }

            default String path() {
                return "/rapidoc";
            }
        }
    }

    @ConfigMapper
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
