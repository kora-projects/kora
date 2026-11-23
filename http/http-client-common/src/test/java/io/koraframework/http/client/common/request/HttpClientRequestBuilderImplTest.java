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

    @Test
    void testPathParamIsPercentEncoded() {
        var result = HttpClientRequest.get("http://localhost/users/{name}/{other}")
            .pathParam("name", "john doe")
            .pathParam("other", "a+b")
            .build();

        assertEquals(URI.create("http://localhost/users/john%20doe/a%2Bb"), result.uri());
    }

    @Test
    void toBuilderQueryParamRemoveRemovesExistingParam() {
        var original = HttpClientRequest.get("http://localhost/items")
            .queryParam("apiKey", "secret")
            .queryParam("page", "1")
            .build();

        var result = original.toBuilder()
            .queryParamRemove("apiKey")
            .build();

        assertEquals(URI.create("http://localhost/items?page=1"), result.uri());
    }

    @Test
    void toBuilderKeepsExistingQueryEncoding() {
        var original = HttpClientRequest.of("GET", URI.create("http://localhost/items?q=a%20b&flag&page=1"), "/items", HttpHeaders.empty(), HttpBody.empty(), null);

        var removed = original.toBuilder()
            .queryParamRemove("page")
            .build();
        var added = original.toBuilder()
            .queryParam("size", "10")
            .build();

        assertAll(
            () -> assertEquals(URI.create("http://localhost/items?q=a%20b&flag"), removed.uri()),
            () -> assertEquals(URI.create("http://localhost/items?q=a%20b&flag&page=1&size=10"), added.uri())
        );
    }

    @Test
    void toBuilderQueryParamAddsToExistingQuery() {
        var original = HttpClientRequest.get("http://localhost/items")
            .queryParam("page", "1")
            .build();

        var result = original.toBuilder()
            .queryParam("size", "10")
            .build();

        assertEquals(URI.create("http://localhost/items?page=1&size=10"), result.uri());
    }
}
