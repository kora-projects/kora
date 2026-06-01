package io.koraframework.openapi.management;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.util.LinkedHashMap;
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
            var options = new LinkedHashMap<String, String>();
            options.put("layout", "StandaloneLayout");
            options.put("validatorUrl", "null");
            options.put("defaultModelsExpandDepth", "0");
            options.put("deepLinking", "true");
            options.put("persistAuthorization", "true");
            options.put("displayOperationId", "true");
            options.put("filter", "true");
            return options;
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
