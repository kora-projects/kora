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

    /**
     * @return Path prefix of the Camunda 7 REST API.
     */
    default String path() {
        return "/engine-rest";
    }

    /**
     * @return Port of the separate Undertow HTTP server serving the REST API.
     */
    default Integer port() {
        return 8081;
    }

    /**
     * @return Maximum time to wait for the HTTP server graceful shutdown.
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * @return OpenAPI, Swagger UI and RapiDoc serving configuration.
     */
    CamundaOpenApiConfig openapi();

    /**
     * @return Telemetry configuration of the module.
     */
    CamundaRestTelemetryConfig telemetry();

    /**
     * @return CORS filter configuration.
     */
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

        default CacheMode cache() {
            return CacheMode.GZIP;
        }

        /**
         * @return Swagger UI serving configuration.
         */
        SwaggerUIConfig swaggerui();

        ScalarConfig scalar();

        enum CacheMode {
            NONE,
            GZIP,
            FULL
        }

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

            default CacheMode cache() {
                return CacheMode.GZIP;
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
        interface ScalarConfig {

            default boolean enabled() {
                return false;
            }

            default String path() {
                return "/scalar";
            }

            default CacheMode cache() {
                return CacheMode.GZIP;
            }
        }
    }

    @ConfigMapper
    interface CamundaCorsConfig {

        default boolean enabled() {
            return false;
        }

        /**
         * @return Whether the path template is logged instead of the full path, when not specified the full path is used only at TRACE level.
         */
        @Nullable
        String allowOrigin();

        /**
         * @return Allowed headers for CORS requests.
         */
        default List<String> allowHeaders() {
            return List.of("*");
        }

        /**
         * @return Allowed HTTP methods for CORS requests.
         */
        default List<String> allowMethods() {
            return List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
        }

        /**
         * @return Whether credentials are allowed in CORS requests.
         */
        default Boolean allowCredentials() {
            return true;
        }

        /**
         * @return Headers exposed to the client in a CORS response.
         */
        default List<String> exposeHeaders() {
            return List.of("*");
        }

        /**
         * @return Maximum caching time for CORS preflight requests.
         */
        default Duration maxAge() {
            return Duration.ofHours(1);
        }
    }
}
