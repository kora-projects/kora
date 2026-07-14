package io.koraframework.openapi.management;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.common.HttpMethod;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.request.HttpServerRequestHandlerImpl;

public interface OpenApiManagementModule {

    default OpenApiManagementConfig openApiManagementConfig(Config config, ConfigValueMapper<OpenApiManagementConfig> mapper) {
        return mapper.mapOrThrow(config.get("openapi.management"));
    }

    default HttpServerRequestHandler openApiManagementController(OpenApiManagementConfig config) {
        var handler = new OpenApiHttpServerHandler(config.files(), config.cache());
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

    default HttpServerRequestHandler scalarManagementController(OpenApiManagementConfig config) {
        boolean enabled = config.scalar() != null && config.scalar().enabled();
        var handler = new ScalarHttpServerHandler(config.path(), config.scalar(), config.files());
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.scalar().path(), handler, enabled);
    }
}
