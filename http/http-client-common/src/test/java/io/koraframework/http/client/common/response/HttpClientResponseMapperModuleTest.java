package io.koraframework.http.client.common.response;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpClientResponseMapperModuleTest {

    private final HttpClientResponseMapperModule module = new HttpClientResponseMapperModule() {};

    @Test
    void stringMapperDecodesWithContentTypeCharset() throws Exception {
        var cp1251 = Charset.forName("windows-1251");
        var response = response("text/plain; charset=windows-1251", "Привет".getBytes(cp1251));

        assertThat(module.httpClientResponseStringMapper().apply(response)).isEqualTo("Привет");
    }

    @Test
    void stringMapperFallsBackToUtf8WithoutCharset() throws Exception {
        var response = response("text/plain", "Привет".getBytes(StandardCharsets.UTF_8));

        assertThat(module.httpClientResponseStringMapper().apply(response)).isEqualTo("Привет");
    }

    @Test
    void stringMapperFallsBackToUtf8OnUnknownCharset() throws Exception {
        var response = response("text/plain; charset=no-such-charset", "Привет".getBytes(StandardCharsets.UTF_8));

        assertThat(module.httpClientResponseStringMapper().apply(response)).isEqualTo("Привет");
    }

    private static HttpClientResponse response(String contentType, byte[] content) {
        var response = mock(HttpClientResponse.class);
        when(response.body()).thenReturn(HttpBody.of(contentType, content));
        when(response.headers()).thenReturn(HttpHeaders.of("content-type", contentType));
        return response;
    }
}
