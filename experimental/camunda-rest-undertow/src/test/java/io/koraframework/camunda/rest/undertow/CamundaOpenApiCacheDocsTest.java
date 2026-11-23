package io.koraframework.camunda.rest.undertow;

import io.koraframework.camunda.rest.CamundaRestConfig;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.SimpleHttpServerRequest;
import io.koraframework.openapi.management.OpenApiHttpServerHandler;
import io.koraframework.openapi.management.OpenApiManagementConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// docs camunda7-rest.md: the OpenAPI page is "served by the same handlers the OpenAPI management module uses,
// so their behavior matches that module" and cache links to openapi-management.md#cache:
// NONE + Accept-Encoding: gzip -> "resource is read and compressed on every request", Vary on both representations
class CamundaOpenApiCacheDocsTest {

    private static SimpleHttpServerRequest request() {
        return request(HttpHeaders.of("accept-encoding", "gzip"));
    }

    private static SimpleHttpServerRequest request(HttpHeaders headers) {
        return new SimpleHttpServerRequest("localhost", "http", "GET", "/openapi", "/openapi", Map.of(), Map.of(),
            headers, List.of(), HttpBody.empty(), 0);
    }

    @Test
    void noneCacheModeWithGzipMatchesOpenApiManagement() {
        var management = new OpenApiHttpServerHandler(List.of("openapi.json"), OpenApiManagementConfig.CacheMode.NONE).apply(request());
        var camunda = new CamundaOpenApiHttpServerHandler(List.of("openapi.json"), CamundaRestConfig.CamundaOpenApiConfig.CacheMode.NONE, "/engine-rest", 8081).apply(request());

        assertThat(management.headers().getFirst("content-encoding")).isEqualTo("gzip");
        assertThat(camunda.headers().getFirst("content-encoding")).as("camunda content-encoding").isEqualTo("gzip");
        assertThat(camunda.headers().getFirst("vary")).as("camunda vary").isEqualTo("Accept-Encoding");
    }

    @Test
    void noneCacheModeWithoutGzipMatchesOpenApiManagement() {
        var management = new OpenApiHttpServerHandler(List.of("openapi.json"), OpenApiManagementConfig.CacheMode.NONE).apply(request(HttpHeaders.of()));
        var camunda = new CamundaOpenApiHttpServerHandler(List.of("openapi.json"), CamundaRestConfig.CamundaOpenApiConfig.CacheMode.NONE, "/engine-rest", 8081).apply(request(HttpHeaders.of()));

        assertThat(management.headers().getFirst("content-encoding")).isNull();
        assertThat(management.headers().getFirst("vary")).isEqualTo("Accept-Encoding");
        assertThat(camunda.headers().getFirst("content-encoding")).as("camunda content-encoding").isNull();
        assertThat(camunda.headers().getFirst("vary")).as("camunda vary").isEqualTo("Accept-Encoding");
    }
}
