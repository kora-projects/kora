package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

final class RapidocHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    private static final String FILE_PATH = "kora/openapi/management/rapidoc/index.html";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final AtomicReference<byte[]> content = new AtomicReference<>();
    private final OpenApiManagementConfig config;

    RapidocHttpServerHandler(OpenApiManagementConfig config) {
        this.config = config;
    }

    @Override
    public CompletionStage<HttpServerResponse> apply(Context context, HttpServerRequest request) throws Exception {
        byte[] bytes = content.get();
        if (bytes != null) {
            return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, bytes)));
        }

        return CompletableFuture.supplyAsync(() -> {
            byte[] loadedBytes = loadRapidoc(config);
            content.set(loadedBytes);
            return HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, loadedBytes));
        });
    }

    private static byte[] loadRapidoc(OpenApiManagementConfig config) {
        return ResourceUtils.getFileAsString(FILE_PATH)
            .map(file -> {
                var tagRapidoc = "${rapidocPath}";
                int ri = file.lastIndexOf(tagRapidoc);
                var result = file.substring(0, ri) + config.rapidoc().endpoint() + file.substring(ri + tagRapidoc.length());

                String openapiPath = (config.file().size() == 1) ? config.endpoint() : config.endpoint() + "/" + ResourceUtils.getFileName(config.file().get(0));

                var tagOpenapi = "${openapiPath}";
                int oi = result.lastIndexOf(tagOpenapi);
                result = result.substring(0, oi) + openapiPath + result.substring(oi + tagOpenapi.length());

                return result;
            })
            .map(file -> file.getBytes(StandardCharsets.UTF_8))
            .orElseThrow(() -> HttpServerResponseException.of(404, "Rapidoc file not found"));
    }
}
