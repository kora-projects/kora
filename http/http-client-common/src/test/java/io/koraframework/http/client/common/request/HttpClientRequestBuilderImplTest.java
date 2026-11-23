package io.koraframework.http.client.common.request;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpClientRequestBuilderImplTest {

    @Test
    void testBuildWithQuery() {
        var result = HttpClientRequest.post("/foo/{bar}/baz")
            .pathParam("bar", "rab")
            .queryParam("qw+e", "a+sd")
            .queryParam("zxc", "cxz")
            .build();

        assertAll(
            () -> assertEquals("POST", result.method()),
            () -> assertEquals(URI.create("/foo/rab/baz?qw%2Be=a%2Bsd&zxc=cxz"), result.uri())
        );
    }

    @Test
    void testBuildWithQueryWhenTemplateAlreadyHasQuery() {
        assertAll(
            () -> assertEquals(URI.create("http://h/search?fixed=1&q=x"), HttpClientRequest.get("http://h/search?fixed=1").queryParam("q", "x").build().uri()),
            () -> assertEquals(URI.create("http://h/search?q=x"), HttpClientRequest.get("http://h/search?").queryParam("q", "x").build().uri()),
            () -> assertEquals(URI.create("http://h/search?fixed=1&q=x"), HttpClientRequest.get("http://h/search?fixed=1&").queryParam("q", "x").build().uri()),
            () -> assertEquals(URI.create("/foo/rab?fixed=1&q=x"), HttpClientRequest.get("/foo/{bar}?fixed=1").pathParam("bar", "rab").queryParam("q", "x").build().uri())
        );
    }

    @Test
    void toBuilderCopiesHeadersOnlyOnMutation() {
        var original = HttpClientRequest.of(
            "GET",
            URI.create("/foo"),
            "/foo",
            HttpHeaders.of("test-header", "original"),
            HttpBody.empty(),
            null
        );

        var result = original.toBuilder()
            .header("test-header", "updated")
            .build();

        assertAll(
            () -> assertEquals("original", original.headers().getFirst("test-header")),
            () -> assertEquals("updated", result.headers().getFirst("test-header"))
        );
    }
}
