package io.koraframework.openapi.management;


import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class SwaggerOauthHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    public static final String PATH = "/oauth2-redirect";

    private static final String FILE_PATH = "kora/openapi/management/swagger-ui/oauth2-redirect.page";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final AtomicReference<byte[]> content = new AtomicReference<>();

    @Override
    public HttpServerResponse apply(HttpServerRequest request) {
        byte[] bytes = content.get();
        if (bytes != null) {
            return HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, bytes));
        }

        byte[] loadedBytes = loadSwagger();
        content.set(loadedBytes);
        return HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, loadedBytes));
    }

    private byte[] loadSwagger() {
        return Optional.ofNullable(ResourceUtils.getFileAsString(FILE_PATH))
            .map(file -> file.getBytes(StandardCharsets.UTF_8))
            .orElseThrow(() -> HttpServerResponseException.of(404, "Swagger UI OAUTH2 Redirect file not found"));
    }
}
