package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;

import java.util.concurrent.CompletableFuture;

public interface OpenApiManagementModule {

    default OpenApiManagementConfig openApiManagementConfig(Config config, ConfigValueExtractor<OpenApiManagementConfig> extractor) {
        return extractor.extract(config.get("openapi.management"));
    }

    default HttpServerRequestHandler openApiManagementController(OpenApiManagementConfig config) {
        if (!config.enabled()) {
            return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.endpoint(),
                    (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new OpenApiHttpServerHandler(config);
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.endpoint(), handler);
    }

    default HttpServerRequestHandler swaggerUIManagementController(OpenApiManagementConfig config) {
        if (config.swaggerui() == null || !config.swaggerui().enabled()) {
            return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.rapidoc().endpoint(),
                    (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new SwaggerUIHttpServerHandler(config);
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.swaggerui().endpoint(), handler);
    }

    default HttpServerRequestHandler rapidocManagementController(OpenApiManagementConfig config) {
        if (config.rapidoc() == null || !config.rapidoc().enabled()) {
            return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.rapidoc().endpoint(),
                    (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new RapidocHttpServerHandler(config);
        return HttpServerRequestHandlerImpl.of(HttpMethod.GET, config.rapidoc().endpoint(), handler);
    }
}
