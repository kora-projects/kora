package io.koraframework.openapi.management;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.util.List;
import java.util.Map;

@ConfigValueExtractor
public interface OpenApiManagementConfig {

    default boolean enabled() {
        return false;
    }

    List<String> files();

    default String path() {
        return "/openapi";
    }

    SwaggerUIConfig swaggerui();

    RapidocConfig rapidoc();

    @ConfigValueExtractor
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

    @ConfigValueExtractor
    interface RapidocConfig {

        default boolean enabled() {
            return false;
        }

        default String path() {
            return "/rapidoc";
        }
    }
}
