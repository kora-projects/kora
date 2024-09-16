package ru.tinkoff.kora.openapi.management;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.List;

@ConfigValueExtractor
public interface OpenApiManagementConfig {

    List<String> file();

    default boolean enabled() {
        return false;
    }

    default String endpoint() {
        return "/openapi";
    }

    @Nullable
    SwaggerUIConfig swaggerui();

    @Nullable
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
