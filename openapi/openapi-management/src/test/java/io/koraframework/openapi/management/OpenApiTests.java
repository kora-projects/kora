package io.koraframework.openapi.management;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    void swaggerUiDefaultConfig() {
        var handler = new SwaggerUIHttpServerHandler("/openapi", new TestSwaggerUIConfig(), List.of("openapi1.yaml"));

        var html = bodyString(handler.apply(null));

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

        var html = bodyString(handler.apply(null));

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

    private static String bodyString(io.koraframework.http.server.common.response.HttpServerResponse response) {
        var body = response.body();
        assertNotNull(body);
        var bytes = new byte[body.getFullContentIfAvailable().remaining()];
        body.getFullContentIfAvailable().get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
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
}
