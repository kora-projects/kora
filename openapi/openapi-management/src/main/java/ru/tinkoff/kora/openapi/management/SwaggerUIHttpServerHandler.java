package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

final class SwaggerUIHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    private static final String FILE_PATH = "kora/openapi/management/swagger-ui/index.html";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final byte[] swaggerui;

    SwaggerUIHttpServerHandler(OpenApiManagementConfig config) {
        this.swaggerui = loadSwagger(config);
    }

    @Override
    public CompletionStage<HttpServerResponse> apply(Context context, HttpServerRequest request) throws Exception {
        return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, swaggerui)));
    }

    private static byte[] loadSwagger(OpenApiManagementConfig config) {
        return ResourceUtils.getFileAsString(FILE_PATH)
                .map(file -> {
                    var tagSwagger = "${swaggerUIPath}";
                    int ri = file.lastIndexOf(tagSwagger);
                    var result = file.substring(0, ri) + config.swaggerui().endpoint() + file.substring(ri + tagSwagger.length());

                    var tagOpenapi = "${openapiPath}";
                    int oi = result.lastIndexOf(tagOpenapi);
                    result = result.substring(0, oi) + config.endpoint() + result.substring(oi + tagOpenapi.length());

                    return result;
                })
                .map(file -> file.getBytes(StandardCharsets.UTF_8))
                .orElseThrow(() -> HttpServerResponseException.of(404, "Swagger UI file not found"));
    }
}
