package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.List;

@ConfigValueExtractor
public interface OpenApiManagementConfig {

    /**
     * @return Path to an OpenAPI file or list of paths relative to application resources.
     */
    List<String> file();

    /**
     * @return Enables serving OpenAPI files through the HTTP handler.
     */
    default boolean enabled() {
        return false;
    }

    /**
     * @return Path where OpenAPI files are available, used as a prefix of the /openapi/{file} form when multiple files are specified.
     */
    default String endpoint() {
        return "/openapi";
    }

    /**
     * @return Swagger UI page configuration.
     */
    SwaggerUIConfig swaggerui();

    /**
     * @return RapiDoc page configuration.
     */
    RapidocConfig rapidoc();

    @ConfigValueExtractor
    interface SwaggerUIConfig {

        /**
         * @return Enables the Swagger UI page.
         */
        default boolean enabled() {
            return false;
        }

        /**
         * @return Path where the Swagger UI page is available.
         */
        default String endpoint() {
            return "/swagger-ui";
        }
    }

    @ConfigValueExtractor
    interface RapidocConfig {

        /**
         * @return Enables the RapiDoc page.
         */
        default boolean enabled() {
            return false;
        }

        /**
         * @return Path where the RapiDoc page is available.
         */
        default String endpoint() {
            return "/rapidoc";
        }
    }
}
