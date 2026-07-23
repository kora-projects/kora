package ru.tinkoff.kora.camunda.rest;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ConfigValueExtractor
public interface CamundaRestConfig {

    /**
     * @return Whether the Camunda 7 REST API is enabled.
     */
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
     * @return CORS filter configuration.
     */
    CamundaCorsConfig cors();

    /**
     * @return Telemetry configuration of the module.
     */
    CamundaRestTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface CamundaRestTelemetryConfig extends TelemetryConfig {

        /**
         * @return Logging telemetry configuration.
         */
        @Override
        CamundaRestLoggerConfig logging();
    }

    @ConfigValueExtractor
    interface CamundaRestLoggerConfig extends TelemetryConfig.LogConfig {

        /**
         * @return Whether stack traces are logged when an exception occurs.
         */
        default boolean stacktrace() {
            return true;
        }

        /**
         * @return Request query parameters hidden in logs.
         */
        default Set<String> maskQueries() {
            return Collections.emptySet();
        }

        /**
         * @return Request and response headers hidden in logs.
         */
        default Set<String> maskHeaders() {
            return Set.of("authorization");
        }

        /**
         * @return Mask used to hide the specified headers and query parameters.
         */
        default String mask() {
            return "***";
        }

        /**
         * @return Whether the path template is logged instead of the full path, when not specified the full path is used only at TRACE level.
         */
        @Nullable
        Boolean pathTemplate();
    }

    @ConfigValueExtractor
    interface CamundaOpenApiConfig {

        /**
         * @return Paths to the OpenAPI files in resources.
         */
        default List<String> file() {
            return List.of("openapi.json");
        }

        /**
         * @return Whether the controller serving the OpenAPI file is enabled.
         */
        default boolean enabled() {
            return false;
        }

        /**
         * @return Path where the OpenAPI file is available.
         */
        default String endpoint() {
            return "/openapi";
        }

        /**
         * @return Swagger UI serving configuration.
         */
        SwaggerUIConfig swaggerui();

        /**
         * @return RapiDoc serving configuration.
         */
        RapidocConfig rapidoc();

        @ConfigValueExtractor
        interface SwaggerUIConfig {

            /**
             * @return Whether the controller serving Swagger UI is enabled.
             */
            default boolean enabled() {
                return false;
            }

            /**
             * @return Path where Swagger UI is available.
             */
            default String endpoint() {
                return "/swagger-ui";
            }
        }

        @ConfigValueExtractor
        interface RapidocConfig {

            /**
             * @return Whether the controller serving RapiDoc is enabled.
             */
            default boolean enabled() {
                return false;
            }

            /**
             * @return Path where RapiDoc is available.
             */
            default String endpoint() {
                return "/rapidoc";
            }
        }
    }

    @ConfigValueExtractor
    interface CamundaCorsConfig {

        /**
         * @return Whether the CORS filter is enabled.
         */
        default boolean enabled() {
            return false;
        }

        /**
         * @return Allowed origin for CORS requests, when not specified the request Origin header is reflected back.
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
