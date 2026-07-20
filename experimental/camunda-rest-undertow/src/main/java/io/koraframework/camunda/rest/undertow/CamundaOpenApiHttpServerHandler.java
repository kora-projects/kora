package io.koraframework.camunda.rest.undertow;

import io.koraframework.camunda.rest.CamundaRestConfig;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

final class CamundaOpenApiHttpServerHandler implements HttpServerRequestHandler.HandlerFunction {

    private static final String ACCEPT_ENCODING = "Accept-Encoding";
    private static final String CONTENT_ENCODING = "Content-Encoding";
    private static final String VARY = "Vary";
    private static final String GZIP = "gzip";

    private static final class OpenapiFile {
        private final String fileName;
        private final String filePath;
        private final String contentType;
        private volatile byte[] gzip;
        private volatile byte[] plain;

        private OpenapiFile(String fileName, String filePath, String contentType) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.contentType = contentType;
        }
    }

    private final Map<String, OpenapiFile> openapiFiles = new ConcurrentHashMap<>();
    private final CamundaRestConfig.CamundaOpenApiConfig.CacheMode cacheMode;
    private final String restPath;
    private final int restPort;

    CamundaOpenApiHttpServerHandler(List<String> openapiFiles, CamundaRestConfig.CamundaOpenApiConfig.CacheMode cacheMode, String restPath, int restPort) {
        this.cacheMode = cacheMode;
        this.restPath = restPath;
        this.restPort = restPort;
        for (var filePath : openapiFiles) {
            var contentType = filePath.endsWith(".json")
                ? "text/json; charset=utf-8"
                : "text/x-yaml; charset=utf-8";
            var fileName = getFileName(filePath);
            this.openapiFiles.put(fileName, new OpenapiFile(fileName, filePath, contentType));
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

        var openapiFile = openapiFiles.get(fileName);
        if (openapiFile == null) {
            return HttpServerResponse.of(404, HttpBody.plaintext("OpenAPI file not registered: " + fileName));
        }

        if (cacheMode == CamundaRestConfig.CamundaOpenApiConfig.CacheMode.NONE) {
            return HttpServerResponse.of(200, HttpBody.of(openapiFile.contentType, loadOpenapi(openapiFile.filePath)));
        }

        if (acceptsGzip(request)) {
            var body = gzip(openapiFile);
            return HttpServerResponse.of(200, HttpHeaders.of(CONTENT_ENCODING, GZIP, VARY, ACCEPT_ENCODING), HttpBody.of(openapiFile.contentType, body));
        }

        var body = switch (cacheMode) {
            case FULL -> plain(openapiFile);
            case GZIP -> loadOpenapi(openapiFile.filePath);
            case NONE -> throw new IllegalStateException("NONE cache mode should use uncached response");
        };
        return HttpServerResponse.of(200, HttpHeaders.of(VARY, ACCEPT_ENCODING), HttpBody.of(openapiFile.contentType, body));
    }

    private byte[] plain(OpenapiFile openapiFile) {
        var result = openapiFile.plain;
        if (result == null) {
            synchronized (openapiFile) {
                result = openapiFile.plain;
                if (result == null) {
                    result = loadOpenapi(openapiFile.filePath);
                    openapiFile.plain = result;
                }
            }
        }
        return result;
    }

    private byte[] gzip(OpenapiFile openapiFile) {
        var result = openapiFile.gzip;
        if (result == null) {
            synchronized (openapiFile) {
                result = openapiFile.gzip;
                if (result == null) {
                    result = gzip(loadOpenapi(openapiFile.filePath));
                    openapiFile.gzip = result;
                }
            }
        }
        return result;
    }

    private byte[] loadOpenapi(String filePath) {
        try (var openapiAsStream = getFileAsStream(filePath)) {
            if (openapiAsStream == null) {
                throw HttpServerResponseException.of(404, "OpenAPI file not found while reading: " + filePath);
            }

            return mapOpenapi(openapiAsStream.readAllBytes());
        } catch (IOException e) {
            throw HttpServerResponseException.of(500, "Can't read OpenAPI file: " + filePath);
        }
    }

    private byte[] mapOpenapi(byte[] fileContent) {
        var fileAsStr = new String(fileContent, StandardCharsets.UTF_8);
        if ("/engine-rest".equals(restPath)) {
            return fileAsStr
                .replace("8080", String.valueOf(restPort))
                .getBytes(StandardCharsets.UTF_8);
        }

        var newEnginePath = restPath.startsWith("/")
            ? restPath.substring(1)
            : restPath;
        return fileAsStr
            .replace("engine-rest", newEnginePath)
            .replace("8080", String.valueOf(restPort))
            .getBytes(StandardCharsets.UTF_8);
    }

    private static boolean acceptsGzip(HttpServerRequest request) {
        if (request == null || request.headers() == null) {
            return false;
        }
        var value = request.headers().getFirst(ACCEPT_ENCODING);
        if (value == null || value.isBlank()) {
            return false;
        }
        for (var part : value.split(",")) {
            var encoding = part.trim().toLowerCase(Locale.ROOT);
            var parameters = encoding.split(";");
            if (!parameters[0].trim().equals(GZIP)) {
                continue;
            }
            for (var parameter : parameters) {
                if (parameter.trim().matches("q=0(?:\\.0*)?")) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] gzip(byte[] content) {
        try {
            var out = new ByteArrayOutputStream(content.length);
            try (var gzip = new GZIPOutputStream(out)) {
                gzip.write(content);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Can't gzip OpenAPI file", e);
        }
    }

    private static BufferedInputStream getFileAsStream(String path) {
        var resourceAsStream = CamundaOpenApiHttpServerHandler.class.getResourceAsStream(path);
        if (resourceAsStream != null) {
            return new BufferedInputStream(resourceAsStream);
        }
        var resourceWithSlashStream = CamundaOpenApiHttpServerHandler.class.getResourceAsStream("/" + path);
        if (resourceWithSlashStream != null) {
            return new BufferedInputStream(resourceWithSlashStream);
        }
        return null;
    }

    private static String getFileName(String filePath) {
        int i = filePath.lastIndexOf('/');
        String fileName = (i == -1) ? filePath : filePath.substring(i + 1);
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        } else if (fileName.endsWith(".yml")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        } else if (fileName.endsWith(".yaml")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }

        return fileName;
    }
}
