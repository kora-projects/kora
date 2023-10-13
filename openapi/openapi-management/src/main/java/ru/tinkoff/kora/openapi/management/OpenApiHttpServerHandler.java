package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class OpenApiHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    private final String contentType;
    private final byte[] openapi;

    OpenApiHttpServerHandler(OpenApiManagementConfig config) {
        this.contentType = config.file().endsWith(".json")
                ? "text/json; charset=utf-8"
                : "text/x-yaml; charset=utf-8";

        this.openapi = loadOpenapi(config);
    }

    @Override
    public CompletionStage<HttpServerResponse> apply(Context context, HttpServerRequest request) throws Exception {
        return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.of(contentType, openapi)));
    }

    private static byte[] loadOpenapi(OpenApiManagementConfig config) {
        try {
            var openapiAsStream = ResourceUtils.getFileAsStream(config.file());
            if (openapiAsStream == null) {
                throw HttpServerResponseException.of(404, "Can't read direct OpenAPI file: " + config.file());
            }

            return openapiAsStream.readAllBytes();
        } catch (IOException e) {
            throw HttpServerResponseException.of(500, "Can't read direct OpenAPI file: " + config.file());
        }
    }
}
