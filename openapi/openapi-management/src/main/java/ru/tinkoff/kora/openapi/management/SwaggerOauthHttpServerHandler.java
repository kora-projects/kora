package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

public final class SwaggerOauthHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    public static final String PATH = "/oauth2-redirect";

    private static final String FILE_PATH = "kora/openapi/management/swagger-ui/oauth2-redirect.html";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final AtomicReference<byte[]> content = new AtomicReference<>();

    @Override
    public CompletionStage<HttpServerResponse> apply(Context context, HttpServerRequest request) {
        byte[] bytes = content.get();
        if (bytes != null) {
            return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, bytes)));
        }

        return CompletableFuture.supplyAsync(() -> {
            byte[] loadedBytes = loadSwagger();
            content.set(loadedBytes);
            return HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, loadedBytes));
        });
    }

    private byte[] loadSwagger() {
        return ResourceUtils.getFileAsString(FILE_PATH)
            .map(file -> file.getBytes(StandardCharsets.UTF_8))
            .orElseThrow(() -> HttpServerResponseException.of(404, "Swagger UI OAUTH2 Redirect file not found"));
    }
}
