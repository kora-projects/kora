package io.koraframework.camunda.rest;

import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetryConfig;
import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.openapi.management.OpenApiManagementConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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

    CamundaRestTelemetryConfig telemetry();

    CamundaOpenApiConfig.CamundaCorsConfig cors();

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

        OpenApiManagementConfig.SwaggerUIConfig swaggerui();

        OpenApiManagementConfig.RapidocConfig rapidoc();

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
}
