package io.koraframework.openapi.management;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public final class ScalarHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    private static final String FILE_PATH = "kora/openapi/management/scalar/index.html";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final String openapiPath;
    private final OpenApiManagementConfig.ScalarConfig scalar;
    private final List<String> openapiFiles;
    private final CacheHttpServerResponse response;

    public ScalarHttpServerHandler(String openapiPath, OpenApiManagementConfig.ScalarConfig scalar, List<String> openapiFiles) {
        this.openapiPath = openapiPath;
        this.scalar = scalar;
        this.openapiFiles = openapiFiles;
        this.response = new CacheHttpServerResponse(HTML_CONTENT_TYPE, scalar.cache(), this::loadScalar);
    }

    @Override
    public HttpServerResponse apply(HttpServerRequest request) {
        return response.response(request);
    }

    private byte[] loadScalar() {
        return Optional.ofNullable(ResourceUtils.getFileAsString(FILE_PATH))
            .map(file -> {
                var tagScalar = "${scalarSources}";
                var ri = file.lastIndexOf(tagScalar);
                if (ri < 0) {
                    throw new IllegalStateException("Scalar file doesn't contain ${scalarSources} placeholder");
                }
                return file.substring(0, ri) + scalarSources() + file.substring(ri + tagScalar.length());
            })
            .map(file -> file.getBytes(StandardCharsets.UTF_8))
            .orElseThrow(() -> HttpServerResponseException.of(404, "Scalar file not found"));
    }

    private String scalarSources() {
        var sources = new StringBuilder();
        for (String filePath : openapiFiles) {
            var fileName = ResourceUtils.getFileName(filePath);
            sources.append("""
                { url: window.location.href.substring(0, window.location.href.lastIndexOf("#") === -1 ? window.location.href.length : window.location.href.lastIndexOf("#")).replace("%s", "%s"),
                  title: "%s" },
                """.formatted(jsString(scalar.path()), jsString(openapiFiles.size() == 1 ? openapiPath : openapiPath + "/" + fileName), jsString(fileName)));
        }
        return sources.toString();
    }

    private static String jsString(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
