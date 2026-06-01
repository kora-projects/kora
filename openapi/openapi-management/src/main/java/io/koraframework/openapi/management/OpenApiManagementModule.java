package io.koraframework.openapi.management;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.common.HttpMethod;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.request.HttpServerRequestHandlerImpl;

public interface OpenApiManagementModule {

    default OpenApiManagementConfig openApiManagementConfig(Config config, ConfigValueExtractor<OpenApiManagementConfig> extractor) {
        return extractor.extractOrThrow(config.get("openapi.management"));
    }

    default HttpServerRequestHandler openApiManagementController(OpenApiManagementConfig config) {
        var handler = new OpenApiHttpServerHandler(config.files(), f -> f);
        final String path = (config.files().size() == 1) ? config.path() : config.path() + "/{file}";
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, path, handler, config.enabled());
    }

    default HttpServerRequestHandler swaggerUIManagementController(OpenApiManagementConfig config) {
        boolean enabled = config.swaggerui() != null && config.swaggerui().enabled();
        var handler = new SwaggerUIHttpServerHandler(config.path(), config.swaggerui(), config.files());
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.swaggerui().path(), handler, enabled);
    }

    default HttpServerRequestHandler swaggerOauthManagementController(OpenApiManagementConfig config) {
        boolean enabled = config.swaggerui() != null && config.swaggerui().enabled();
        var handler = new SwaggerOauthHttpServerHandler();
        var path = config.swaggerui().path() + SwaggerOauthHttpServerHandler.PATH;
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, path, handler, enabled);
    }

    default HttpServerRequestHandler rapidocManagementController(OpenApiManagementConfig config) {
        boolean enabled = config.rapidoc() != null && config.rapidoc().enabled();
        var handler = new RapidocHttpServerHandler(config.path(), config.rapidoc().path(), config.files());
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.rapidoc().path(), handler, enabled);
    }
}
