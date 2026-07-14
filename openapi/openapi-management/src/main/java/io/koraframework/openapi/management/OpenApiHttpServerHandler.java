package io.koraframework.openapi.management;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenApiHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    record OpenapiFile(String fileName, String filePath, CacheHttpServerResponse response) {}

    private final Map<String, OpenapiFile> openapiFiles = new ConcurrentHashMap<>();

    public OpenApiHttpServerHandler(List<String> openapiFiles, OpenApiManagementConfig.CacheMode cacheMode) {
        for (var filePath : openapiFiles) {
            var contentType = filePath.endsWith(".json")
                ? "text/json; charset=utf-8"
                : "text/x-yaml; charset=utf-8";
            var fileName = ResourceUtils.getFileName(filePath);
            this.openapiFiles.put(fileName, new OpenapiFile(
                fileName,
                filePath,
                new CacheHttpServerResponse(contentType, cacheMode, () -> loadOpenapi(filePath))
            ));
        }
    }

    @Override
    public HttpServerResponse apply(HttpServerRequest request) {
        var fileName = (openapiFiles.size() == 1)
            ? openapiFiles.keySet().iterator().next()
            : request.pathParams().get("file");
        if (fileName == null || fileName.isEmpty()) {
            return HttpServerResponse.of(400, HttpBody.plaintext("OpenAPI file not specified"));
        }

        final OpenapiFile openapiFile = openapiFiles.get(fileName);
        if (openapiFile == null) {
            return HttpServerResponse.of(404, HttpBody.plaintext("OpenAPI file not registered: " + fileName));
        }

        return openapiFile.response().response(request);
    }

    private static byte[] loadOpenapi(String filePath) {
        try (var openapiAsStream = ResourceUtils.getFileAsStream(filePath)) {
            if (openapiAsStream == null) {
                throw HttpServerResponseException.of(404, "OpenAPI file not found while reading: " + filePath);
            }

            return openapiAsStream.readAllBytes();
        } catch (IOException e) {
            throw HttpServerResponseException.of(500, "Can't read OpenAPI file: " + filePath);
        }
    }
}
