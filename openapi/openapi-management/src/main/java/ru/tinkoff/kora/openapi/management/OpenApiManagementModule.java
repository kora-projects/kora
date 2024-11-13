package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;

import java.util.concurrent.CompletableFuture;

public interface OpenApiManagementModule {

    default OpenApiManagementConfig openApiManagementConfig(Config config, ConfigValueExtractor<OpenApiManagementConfig> extractor) {
        return extractor.extract(config.get("openapi.management"));
    }

    default HttpServerRequestHandler openApiManagementController(OpenApiManagementConfig config) {
        final String path = (config.file().size() == 1) ? config.endpoint() : config.endpoint() + "/{file}";
        if (!config.enabled()) {
            return HttpServerRequestHandlerImpl.get(path,
                (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new OpenApiHttpServerHandler(config.file(), f -> f);
        return HttpServerRequestHandlerImpl.get(path, handler);
    }

    default HttpServerRequestHandler swaggerUIManagementController(OpenApiManagementConfig config) {
        if (config.swaggerui() == null) {
            return HttpServerRequestHandlerImpl.get("/swagger-ui",
                (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        } else if (!config.enabled() || !config.swaggerui().enabled()) {
            return HttpServerRequestHandlerImpl.get(config.swaggerui().endpoint(),
                (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new SwaggerUIHttpServerHandler(config.endpoint(), config.swaggerui().endpoint(), config.file());
        return HttpServerRequestHandlerImpl.get(config.swaggerui().endpoint(), handler);
    }

    default HttpServerRequestHandler rapidocManagementController(OpenApiManagementConfig config) {
        if (config.rapidoc() == null) {
            return HttpServerRequestHandlerImpl.get("/rapidoc",
                (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        } else if (!config.enabled() || !config.rapidoc().enabled()) {
            return HttpServerRequestHandlerImpl.get(config.rapidoc().endpoint(),
                (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new RapidocHttpServerHandler(config.endpoint(), config.rapidoc().endpoint(), config.file());
        return HttpServerRequestHandlerImpl.get(config.rapidoc().endpoint(), handler);
    }
}
