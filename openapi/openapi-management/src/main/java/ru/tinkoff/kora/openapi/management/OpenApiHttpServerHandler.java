package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class OpenApiHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    record OpenapiFile(String fileName, String filePath, String contentType, AtomicReference<byte[]> content) {}

    private final Map<String, OpenapiFile> openapiFiles = new ConcurrentHashMap<>();
    private final Function<byte[], byte[]> openApiMapper;

    public OpenApiHttpServerHandler(List<String> openapiFiles, Function<byte[], byte[]> openApiMapper) {
        this.openApiMapper = openApiMapper;
        for (String filePath : openapiFiles) {
            var contentType = filePath.endsWith(".json")
                ? "text/json; charset=utf-8"
                : "text/x-yaml; charset=utf-8";
            final String fileName = ResourceUtils.getFileName(filePath);
            this.openapiFiles.put(fileName, new OpenapiFile(fileName, filePath, contentType, new AtomicReference<>()));
        }
    }

    @Override
    public CompletionStage<HttpServerResponse> apply(Context context, HttpServerRequest request) {
        final String fileName = (openapiFiles.size() == 1)
            ? openapiFiles.keySet().iterator().next()
            : request.pathParams().get("file");
        if (fileName == null || fileName.isEmpty()) {
            return CompletableFuture.completedFuture(HttpServerResponse.of(400, HttpBody.plaintext("OpenAPI file not specified")));
        }

        final OpenapiFile openapiFile = openapiFiles.get(fileName);
        if (openapiFile == null) {
            return CompletableFuture.completedFuture(HttpServerResponse.of(404, HttpBody.plaintext("OpenAPI file not registered: " + fileName)));
        }

        byte[] bytes = openapiFile.content().get();
        if (bytes != null) {
            return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.of(openapiFile.contentType(), bytes)));
        }

        return CompletableFuture.supplyAsync(() -> {
            byte[] fileContent = loadOpenapi(openapiFile.filePath());
            byte[] fileResult = openApiMapper.apply(fileContent);
            openapiFile.content().set(fileResult);
            return HttpServerResponse.of(200, HttpBody.of(openapiFile.contentType(), fileResult));
        });
    }

    private byte[] loadOpenapi(String filePath) {
        try {
            var openapiAsStream = ResourceUtils.getFileAsStream(filePath);
            if (openapiAsStream == null) {
                throw HttpServerResponseException.of(404, "OpenAPI file not found while reading: " + filePath);
            }

            return openapiAsStream.readAllBytes();
        } catch (IOException e) {
            throw HttpServerResponseException.of(500, "Can't read OpenAPI file: " + filePath);
        }
    }
}
