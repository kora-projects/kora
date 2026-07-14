package io.koraframework.openapi.management;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.SimpleHttpServerRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OpenApiTests {

    @Test
    void getAsString() {
        var openAPI = ResourceUtils.getFileAsString("openapi1.yaml");
        assertNotNull(openAPI);

        assertFalse(openAPI.isBlank());
    }

    @Test
    void getAsBytes() throws IOException {
        try (var openAPI = ResourceUtils.getFileAsStream("openapi1.yaml");) {
            assertNotNull(openAPI);

            assertFalse(new String(openAPI.readAllBytes(), StandardCharsets.UTF_8).isBlank());
        }
    }

    @Test
    void openApiGzipByDefault() throws IOException {
        var handler = new OpenApiHttpServerHandler(List.of("openapi1.yaml"), OpenApiManagementConfig.CacheMode.GZIP);

        var response = handler.apply(request("gzip"));

        assertTrue(response.headers().getAll("content-encoding").contains("gzip"));
        assertTrue(gunzip(response).contains("openapi: 3.0.1"));
    }

    @Test
    void openApiDoesNotGzipWhenDisabledByQ() {
        var handler = new OpenApiHttpServerHandler(List.of("openapi1.yaml"), OpenApiManagementConfig.CacheMode.GZIP);

        var response = handler.apply(request("gzip;q=0"));

        assertFalse(response.headers().has("content-encoding"));
        assertTrue(bodyString(response).contains("openapi: 3.0.1"));
    }

    @Test
    void swaggerUiDefaultConfig() {
        var handler = new SwaggerUIHttpServerHandler("/openapi", new TestSwaggerUIConfig(), List.of("openapi1.yaml"));

        var html = bodyString(handler.apply(request("")));

        assertTrue(html.contains("\"layout\": \"StandaloneLayout\""));
        assertTrue(html.contains("\"validatorUrl\": null"));
        assertTrue(html.contains("\"defaultModelsExpandDepth\": 0"));
        assertTrue(html.contains("\"deepLinking\": true"));
        assertTrue(html.contains("\"persistAuthorization\": true"));
        assertTrue(html.contains("withCredentials: true"));
        assertTrue(html.contains("\"displayOperationId\": true"));
        assertTrue(html.contains("\"filter\": true"));
        assertTrue(html.contains("request.credentials = \"include\""));
    }

    @Test
    void swaggerUiGzipByDefault() throws IOException {
        var handler = new SwaggerUIHttpServerHandler("/openapi", new TestSwaggerUIConfig(), List.of("openapi1.yaml"));

        var response = handler.apply(request("gzip"));

        assertTrue(response.headers().getAll("content-encoding").contains("gzip"));
        assertTrue(response.headers().getAll("vary").contains("Accept-Encoding"));
        assertTrue(gunzip(response).contains("SwaggerUIBundle"));
    }

    @Test
    void swaggerUiDoesNotGzipWhenDisabledByQ() {
        var handler = new SwaggerUIHttpServerHandler("/openapi", new TestSwaggerUIConfig(), List.of("openapi1.yaml"));

        var response = handler.apply(request("gzip;q=0"));

        assertFalse(response.headers().has("content-encoding"));
        assertTrue(bodyString(response).contains("SwaggerUIBundle"));
    }

    @Test
    void swaggerUiCustomConfig() {
        var config = new TestSwaggerUIConfig() {
            @Override
            public boolean withCredentials() {
                return false;
            }

            @Override
            public Map<String, String> options() {
                var options = new LinkedHashMap<String, String>();
                options.put("layout", "BaseLayout");
                options.put("validatorUrl", "https://validator.example.com");
                options.put("defaultModelsExpandDepth", "-1");
                options.put("deepLinking", "false");
                options.put("persistAuthorization", "false");
                options.put("displayOperationId", "false");
                options.put("filter", "false");
                options.put("syntaxHighlight", "{ activated: false }");
                options.put("onComplete", "() => window.swaggerReady = true");
                return options;
            }
        };
        var handler = new SwaggerUIHttpServerHandler("/openapi", config, List.of("openapi1.yaml"));

        var html = bodyString(handler.apply(request("")));

        assertTrue(html.contains("\"layout\": \"BaseLayout\""));
        assertTrue(html.contains("\"validatorUrl\": \"https://validator.example.com\""));
        assertTrue(html.contains("\"defaultModelsExpandDepth\": -1"));
        assertTrue(html.contains("\"deepLinking\": false"));
        assertTrue(html.contains("\"persistAuthorization\": false"));
        assertTrue(html.contains("withCredentials: false"));
        assertTrue(html.contains("\"displayOperationId\": false"));
        assertTrue(html.contains("\"filter\": false"));
        assertTrue(html.contains("\"syntaxHighlight\": { activated: false }"));
        assertTrue(html.contains("\"onComplete\": () => window.swaggerReady = true"));
        assertFalse(html.contains("request.credentials = \"include\""));
    }

    @Test
    void scalarSourcesSingleFile() {
        var handler = new ScalarHttpServerHandler("/openapi", new TestScalarConfig(), List.of("openapi1.yaml"));

        var html = bodyString(handler.apply(request("")));

        assertTrue(html.contains("sources: ["));
        assertTrue(html.contains(".replace(\"/scalar\", \"/openapi\")"));
        assertTrue(html.contains("title: \"openapi1\""));
        assertFalse(html.contains("${scalarSources}"));
        assertFalse(html.contains("gist.githubusercontent.com"));
    }

    @Test
    void scalarSourcesMultipleFiles() {
        var handler = new ScalarHttpServerHandler("/openapi", new TestScalarConfig(), List.of("openapi1.yaml", "openapi2.yaml"));

        var html = bodyString(handler.apply(request("")));

        assertTrue(html.contains(".replace(\"/scalar\", \"/openapi/openapi1\")"));
        assertTrue(html.contains(".replace(\"/scalar\", \"/openapi/openapi2\")"));
        assertTrue(html.contains("title: \"openapi1\""));
        assertTrue(html.contains("title: \"openapi2\""));
    }

    private static String bodyString(io.koraframework.http.server.common.response.HttpServerResponse response) {
        var body = response.body();
        assertNotNull(body);
        var content = body.getFullContentIfAvailable();
        assertNotNull(content);
        var bytes = new byte[content.remaining()];
        content.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String gunzip(io.koraframework.http.server.common.response.HttpServerResponse response) throws IOException {
        var body = response.body();
        assertNotNull(body);
        var content = body.getFullContentIfAvailable();
        assertNotNull(content);
        var bytes = new byte[content.remaining()];
        content.get(bytes);
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static SimpleHttpServerRequest request(String acceptEncoding) {
        return new SimpleHttpServerRequest(
            "localhost",
            "http",
            "GET",
            "/swagger-ui",
            "/swagger-ui",
            Map.of(),
            Map.of(),
            HttpHeaders.of("accept-encoding", acceptEncoding),
            List.of(),
            HttpBody.empty(),
            0
        );
    }

    private static class TestSwaggerUIConfig implements OpenApiManagementConfig.SwaggerUIConfig {
        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public String path() {
            return "/swagger-ui";
        }
    }

    private static class TestScalarConfig implements OpenApiManagementConfig.ScalarConfig {
        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public String path() {
            return "/scalar";
        }
    }
}
