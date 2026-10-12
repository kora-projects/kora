package io.koraframework.http.server.common.request.mapper;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpServerRequestMapperModuleTest {

    private final HttpServerRequestMapperModule module = new HttpServerRequestMapperModule() {};

    @Test
    void stringMapperDecodesWithContentTypeCharset() throws Exception {
        var cp1251 = Charset.forName("windows-1251");
        var request = request("text/plain; charset=windows-1251", "Привет".getBytes(cp1251));

        assertThat(stringMapper().apply(request)).isEqualTo("Привет");
    }

    @Test
    void stringMapperDecodesWithQuotedCaseInsensitiveCharset() throws Exception {
        var request = request("text/plain;Charset=\"UTF-16LE\"", "Привет".getBytes(StandardCharsets.UTF_16LE));

        assertThat(stringMapper().apply(request)).isEqualTo("Привет");
    }

    @Test
    void stringMapperFallsBackToUtf8WithoutCharset() throws Exception {
        var request = request("text/plain", "Привет".getBytes(StandardCharsets.UTF_8));

        assertThat(stringMapper().apply(request)).isEqualTo("Привет");
    }

    @Test
    void stringMapperFallsBackToUtf8OnUnknownCharset() throws Exception {
        var request = request("text/plain; charset=no-such-charset", "Привет".getBytes(StandardCharsets.UTF_8));

        assertThat(stringMapper().apply(request)).isEqualTo("Привет");
    }

    private io.koraframework.http.server.common.request.HttpServerRequestMapper<String> stringMapper() {
        return module.stringHttpServerRequestMapper(module.byteArrayHttpServerRequestMapper());
    }

    private static HttpServerRequest request(String contentType, byte[] content) {
        var request = mock(HttpServerRequest.class);
        when(request.body()).thenReturn(HttpBody.of(contentType, content));
        when(request.headers()).thenReturn(HttpHeaders.of("content-type", contentType));
        return request;
    }
}
