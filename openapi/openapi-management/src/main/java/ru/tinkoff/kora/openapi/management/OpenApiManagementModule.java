package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;

public interface OpenApiManagementModule {

    default OpenApiManagementConfig openApiManagementConfig(Config config, ConfigValueExtractor<OpenApiManagementConfig> extractor) {
        return extractor.extract(config.get("openapi.management"));
    }

    default HttpServerRequestHandler openApiManagementController(OpenApiManagementConfig config) {
        var handler = new OpenApiHttpServerHandler(config.file(), f -> f);
        final String path = (config.file().size() == 1) ? config.endpoint() : config.endpoint() + "/{file}";
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, path, handler, config.enabled());
    }

    default HttpServerRequestHandler swaggerUIManagementController(OpenApiManagementConfig config) {
        boolean enabled = config.swaggerui() != null && config.swaggerui().enabled();
        var handler = new SwaggerUIHttpServerHandler(config.endpoint(), config.swaggerui().endpoint(), config.file());
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.swaggerui().endpoint(), handler, enabled);
    }

    default HttpServerRequestHandler rapidocManagementController(OpenApiManagementConfig config) {
        boolean enabled = config.rapidoc() != null && config.rapidoc().enabled();
        var handler = new RapidocHttpServerHandler(config.endpoint(), config.rapidoc().endpoint(), config.file());
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.rapidoc().endpoint(), handler, enabled);
    }
}
