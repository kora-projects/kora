package io.koraframework.openapi.management;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public final class SwaggerUIHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    private static final String FILE_PATH = "kora/openapi/management/swagger-ui/index.html";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final String openapiPath;
    private final OpenApiManagementConfig.SwaggerUIConfig swaggerui;
    private final List<String> openapiFiles;
    private final CacheHttpServerResponse response;

    public SwaggerUIHttpServerHandler(String openapiPath, OpenApiManagementConfig.SwaggerUIConfig swaggerui, List<String> openapiFiles) {
        this.openapiPath = openapiPath;
        this.swaggerui = swaggerui;
        this.openapiFiles = openapiFiles;
        this.response = new CacheHttpServerResponse(HTML_CONTENT_TYPE, swaggerui.cache(), this::loadSwagger);
    }

    @Override
    public HttpServerResponse apply(HttpServerRequest request) {
        return response.response(request);
    }

    private byte[] loadSwagger() {
        return Optional.ofNullable(ResourceUtils.getFileAsString(FILE_PATH))
            .map(file -> {
                // replace oauth redirect path
                var replacement = swaggerui.path();
                if (replacement.endsWith("/")) {
                    replacement = replacement.substring(0, replacement.length() - 1);
                }
                if (replacement.indexOf("/") != replacement.lastIndexOf("/")) {
                    replacement = replacement.substring(replacement.lastIndexOf("/"));
                }
                replacement += SwaggerOauthHttpServerHandler.PATH;

                var tagSwagger = "${swaggerOauthPath}";
                int ri = file.lastIndexOf(tagSwagger);
                return file.substring(0, ri) + replacement + file.substring(ri + tagSwagger.length());
            })
            .map(file -> {
                if (openapiFiles.size() == 1) {
                    String replacement = """
                        url: window.location.href.substring(0, window.location.href.lastIndexOf("#") === -1 ? window.location.href.length : window.location.href.lastIndexOf("#")).replace("%s", "%s")
                        """.formatted(swaggerui.path(), openapiPath);

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
                            """.formatted(swaggerui.path(), openapiPath + "/" + fileName, fileName));
                    }
                    replacement.append("]");

                    var tagSwagger = "${swaggerUrls}";
                    int ri = file.lastIndexOf(tagSwagger);
                    return file.substring(0, ri) + replacement + file.substring(ri + tagSwagger.length());
                }
            })
            .map(file -> {
                var tagSwagger = "${swaggerOptions}";
                int ri = file.lastIndexOf(tagSwagger);
                return file.substring(0, ri) + swaggerOptions() + file.substring(ri + tagSwagger.length());
            })
            .map(file -> file.getBytes(StandardCharsets.UTF_8))
            .orElseThrow(() -> HttpServerResponseException.of(404, "Swagger UI file not found"));
    }

    private String swaggerOptions() {
        var options = new StringBuilder("""
            dom_id: "#swagger-ui",
            presets: [
                SwaggerUIBundle.presets.apis,
                SwaggerUIStandalonePreset
            ],
            plugins: [
                SwaggerUIBundle.plugins.DownloadUrl,
                f
            ],
            withCredentials: %s
            """.formatted(swaggerui.withCredentials()));

        if (swaggerui.withCredentials()) {
            options.append("""
                ,
                            requestInterceptor: (request) => {
                                request.credentials = "include"
                                return request;
                            }""");
        }

        for (var entry : swaggerui.options().entrySet()) {
            options.append(",\n")
                .append("            ")
                .append(jsString(entry.getKey()))
                .append(": ")
                .append(jsLiteral(entry.getValue()));
        }
        return options.toString();
    }

    private static String jsLiteral(String value) {
        if (value == null) {
            return "null";
        }
        var trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "\"\"";
        }
        if (trimmed.equals("null") || trimmed.equals("true") || trimmed.equals("false")
            || trimmed.matches("-?\\d+(\\.\\d+)?")
            || trimmed.startsWith("{") || trimmed.startsWith("[")
            || trimmed.startsWith("function") || trimmed.startsWith("(")) {
            return trimmed;
        }
        return jsString(value);
    }

    private static String jsString(String value) {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\"";
    }
}
