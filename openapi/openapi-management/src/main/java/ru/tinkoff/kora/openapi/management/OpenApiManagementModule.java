package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface OpenApiManagementModule {

    default OpenApiManagementConfig openApiManagementConfig(Config config, ConfigValueExtractor<OpenApiManagementConfig> extractor) {
        return extractor.extract(config.get("openapi.management"));
    }

    default HttpServerRequestHandler openApiManagementController(OpenApiManagementConfig config) {
        if (!config.enabled()) {
            // random path so the disabled path WILL NOT be blocked and user can reuse such path for himself
            return HttpServerRequestHandlerImpl.get("/openapi-" + UUID.randomUUID(),
                (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new OpenApiHttpServerHandler(config.file(), f -> f);
        final String path = (config.file().size() == 1) ? config.endpoint() : config.endpoint() + "/{file}";
        return HttpServerRequestHandlerImpl.get(path, handler);
    }

    default HttpServerRequestHandler swaggerUIManagementController(OpenApiManagementConfig config) {
        if (config.swaggerui() == null || !config.enabled() || !config.swaggerui().enabled()) {
            // random path so the disabled path WILL NOT be blocked and user can reuse such path for himself
            return HttpServerRequestHandlerImpl.get("/swagger-ui-" + UUID.randomUUID(),
                (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new SwaggerUIHttpServerHandler(config.endpoint(), config.swaggerui().endpoint(), config.file());
        return HttpServerRequestHandlerImpl.get(config.swaggerui().endpoint(), handler);
    }

    default HttpServerRequestHandler rapidocManagementController(OpenApiManagementConfig config) {
        if (config.rapidoc() == null || !config.enabled() || !config.rapidoc().enabled()) {
            // random path so the disabled path WILL NOT be blocked and user can reuse such path for himself
            return HttpServerRequestHandlerImpl.get("/rapidoc-" + UUID.randomUUID(),
                (context, request) -> CompletableFuture.completedFuture(HttpServerResponse.of(404)));
        }

        var handler = new RapidocHttpServerHandler(config.endpoint(), config.rapidoc().endpoint(), config.file());
        return HttpServerRequestHandlerImpl.get(config.rapidoc().endpoint(), handler);
    }
}
