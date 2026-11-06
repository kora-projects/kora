package io.koraframework.http.server.common.response.mapper;

import io.koraframework.http.common.HttpResponseEntity;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class HttpServerResponseEntityMapperTest {

    private final HttpServerResponseMapperModule module = new HttpServerResponseMapperModule() {};
    private final HttpServerRequest request = Mockito.mock(HttpServerRequest.class);

    @Test
    void nullStringBody() throws Exception {
        var rs = apply(module.stringHttpServerResponseMapper(), HttpResponseEntity.of(204, HttpHeaders.of("x-a", "1"), null));
        assertEmpty(rs, 204);
        assertThat(rs.headers().getFirst("x-a")).isEqualTo("1");
    }

    @Test
    void nullByteArrayBody() throws Exception {
        assertEmpty(apply(module.byteArrayHttpServerResponseMapper(), HttpResponseEntity.of(204, HttpHeaders.of(), null)), 204);
    }

    @Test
    void nullByteBufferBody() throws Exception {
        assertEmpty(apply(module.byteBufBodyHttpServerResponseMapper(), HttpResponseEntity.of(204, HttpHeaders.of(), null)), 204);
    }

    @Test
    void nullStringBodyKeepsContentType() throws Exception {
        var rs = apply(module.stringHttpServerResponseMapper(), HttpResponseEntity.of(404, HttpHeaders.of("content-type", "text/plain"), null));
        assertThat(rs.code()).isEqualTo(404);
        assertThat(rs.body()).isNotNull();
        assertThat(rs.body().contentType()).isEqualTo("text/plain");
        assertThat(rs.body().contentLength()).isZero();
    }

    private <T> HttpServerResponse apply(HttpServerResponseMapper<T> delegate, HttpResponseEntity<T> entity) throws Exception {
        return module.httpResponseEntityHttpServerResponseMapper(delegate).apply(request, entity);
    }

    private static void assertEmpty(HttpServerResponse rs, int code) {
        assertThat(rs.code()).isEqualTo(code);
        if (rs.body() != null) {
            assertThat(rs.body().contentLength()).isZero();
        }
    }
}
