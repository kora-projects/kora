package io.koraframework.openapi.management;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.util.List;
import java.util.Map;

@ConfigMapper
public interface OpenApiManagementConfig {

    default boolean enabled() {
        return false;
    }

    List<String> files();

    default String path() {
        return "/openapi";
    }

    default CacheMode cache() {
        return CacheMode.GZIP;
    }

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
