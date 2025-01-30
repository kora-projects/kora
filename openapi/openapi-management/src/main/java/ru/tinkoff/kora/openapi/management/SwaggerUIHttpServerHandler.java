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

public final class SwaggerUIHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    private static final String FILE_PATH = "kora/openapi/management/swagger-ui/index.html";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final AtomicReference<byte[]> content = new AtomicReference<>();
    private final String openapiPath;
    private final String swaggeruiPath;
    private final List<String> openapiFiles;

    public SwaggerUIHttpServerHandler(String openapiPath, String swaggeruiPath, List<String> openapiFiles) {
        this.openapiPath = openapiPath;
        this.swaggeruiPath = swaggeruiPath;
        this.openapiFiles = openapiFiles;
    }

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
            .map(file -> {
                if (openapiFiles.size() == 1) {
                    String replacement = """
                        url: window.location.href.substring(0, window.location.href.lastIndexOf("#") === -1 ? window.location.href.length : window.location.href.lastIndexOf("#")).replace("%s", "%s")
                        """.formatted(swaggeruiPath, openapiPath);

                    var tagSwagger = "${swaggerUrls}";
                    int ri = file.lastIndexOf(tagSwagger);
                    return file.substring(0, ri) + replacement + file.substring(ri + tagSwagger.length());
                } else {
                    final StringBuilder replacement = new StringBuilder("urls: [");
                    for (String filePath : openapiFiles) {
                        final String fileName = ResourceUtils.getFileName(filePath);
                        replacement.append("""
                            { url: window.location.href.substring(0, window.location.href.lastIndexOf("#") === -1 ? window.location.href.length : window.location.href.lastIndexOf("#")).replace("%s", "%s"),
                              name: "%s" },
                            """.formatted(swaggeruiPath, openapiPath + "/" + fileName, fileName));
                    }
                    replacement.append("]");

                    var tagSwagger = "${swaggerUrls}";
                    int ri = file.lastIndexOf(tagSwagger);
                    return file.substring(0, ri) + replacement + file.substring(ri + tagSwagger.length());
                }
            })
            .map(file -> file.getBytes(StandardCharsets.UTF_8))
            .orElseThrow(() -> HttpServerResponseException.of(404, "Swagger UI file not found"));
    }
}
