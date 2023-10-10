package ru.tinkoff.kora.http.client.common.request;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpClientRequestBuilderImplTest {

    @Test
    void testBuildWithQuery() {
        var result = HttpClientRequest.post("/foo/{bar}/baz")
            .templateParam("bar", "rab")
            .queryParam("qw+e", "a+sd")
            .queryParam("zxc", "cxz")
            .build();

        assertAll(
            () -> assertEquals("POST", result.method()),
            () -> assertEquals(URI.create("/foo/rab/baz?qw%2Be=a%2Bsd&zxc=cxz"), result.uri())
        );
    }
}
