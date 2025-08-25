package ru.tinkoff.kora.openapi.management;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class RapidocHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    private static final String FILE_PATH = "kora/openapi/management/rapidoc/index.html";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final AtomicReference<byte[]> content = new AtomicReference<>();
    private final String openapiPath;
    private final String rapidocPath;
    private final List<String> openapiFiles;

    public RapidocHttpServerHandler(String openapiPath, String rapidocPath, List<String> openapiFiles) {
        this.openapiPath = openapiPath;
        this.rapidocPath = rapidocPath;
        this.openapiFiles = openapiFiles;
    }

    @Override
    public HttpServerResponse apply(Context context, HttpServerRequest request) {
        var bytes = content.get();
        if (bytes != null) {
            return HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, bytes));
        }
        var loadedBytes = loadRapidoc();
        content.set(loadedBytes);
        return HttpServerResponse.of(200, HttpBody.of(HTML_CONTENT_TYPE, loadedBytes));
    }

    private byte[] loadRapidoc() {
        var file = ResourceUtils.getFileAsString(FILE_PATH);
        if (file == null) {
            throw HttpServerResponseException.of(404, "Rapidoc file not found");
        }
        var tagRapidoc = "${rapidocPath}";
        var ri = file.lastIndexOf(tagRapidoc);
        var result = file.substring(0, ri) + rapidocPath + file.substring(ri + tagRapidoc.length());

        var openapiFilePath = (openapiFiles.size() == 1)
            ? openapiPath
            : openapiPath + "/" + ResourceUtils.getFileName(openapiFiles.get(0));

        var tagOpenapi = "${openapiPath}";
        var oi = result.lastIndexOf(tagOpenapi);
        result = result.substring(0, oi) + openapiFilePath + result.substring(oi + tagOpenapi.length());

        return result.getBytes(StandardCharsets.UTF_8);
    }
}
