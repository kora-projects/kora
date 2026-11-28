package io.koraframework.http.server.common.response.mapper;

import io.koraframework.common.annotation.*;
import io.koraframework.http.common.*;
import io.koraframework.http.common.body.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.*;

import java.io.*;
import java.nio.*;

public interface HttpServerResponseMapperModule {

    @DefaultComponent
    default HttpServerResponseMapper<HttpServerResponse> noopHttpServerResponseMapper() {
        return new NoopResponseMapper();
    }

    @DefaultComponent
    default HttpServerResponseMapper<ByteBuffer> byteBufBodyHttpServerResponseMapper() {
        return new ByteBufResponseMapper();
    }

    @DefaultComponent
    default HttpServerResponseMapper<byte[]> byteArrayHttpServerResponseMapper() {
        return new ByteArrayResponseMapper();
    }

    @DefaultComponent
    default HttpServerResponseMapper<HttpBodyOutput> httpBodyOutputHttpServerResponseMapper() {
        return new HttpBodyOutputResponseMapper();
    }

    @DefaultComponent
    default HttpServerResponseMapper<String> stringHttpServerResponseMapper() {
        return new StringResponseMapper();
    }

    @DefaultComponent
    default <T> HttpServerResponseMapper<HttpResponseEntity<T>> httpResponseEntityHttpServerResponseMapper(HttpServerResponseMapper<T> delegate) {
        return new HttpServerResponseEntityMapper<>(delegate);
    }

    @Json
    @DefaultComponent
    default <T> HttpServerResponseMapper<HttpResponseEntity<T>> jsonHttpResponseEntityHttpServerResponseMapper(JsonWriter<T> writer) {
        return new HttpServerResponseEntityMapper<>(new JsonWriterHttpServerResponseMapper<>(writer));
    }

    @Json
    @DefaultComponent
    default <T> HttpServerResponseMapper<T> jsonHttpServerResponseMapper(JsonWriter<T> writer) {
        return new JsonWriterHttpServerResponseMapper<>(writer);
    }

    final class NoopResponseMapper implements HttpServerResponseMapper<HttpServerResponse> {
        @Override
        public HttpServerResponse apply(io.koraframework.http.server.common.request.HttpServerRequest request, HttpServerResponse r) throws IOException {
            return r;
        }
    }

    final class ByteBufResponseMapper implements HttpServerResponseMapper<ByteBuffer> {
        @Override
        public HttpServerResponse apply(io.koraframework.http.server.common.request.HttpServerRequest request, ByteBuffer r) throws IOException {
            return HttpServerResponse.of(200, HttpBody.octetStream(r));
        }
    }

    final class ByteArrayResponseMapper implements HttpServerResponseMapper<byte[]> {
        @Override
        public HttpServerResponse apply(io.koraframework.http.server.common.request.HttpServerRequest request, byte[] r) throws IOException {
            return HttpServerResponse.of(200, HttpBody.octetStream(r));
        }
    }

    final class HttpBodyOutputResponseMapper implements HttpServerResponseMapper<HttpBodyOutput> {
        @Override
        public HttpServerResponse apply(io.koraframework.http.server.common.request.HttpServerRequest request, HttpBodyOutput r) throws IOException {
            return HttpServerResponse.of(200, r);
        }
    }

    final class StringResponseMapper implements HttpServerResponseMapper<String> {
        @Override
        public HttpServerResponse apply(io.koraframework.http.server.common.request.HttpServerRequest request, String r) throws IOException {
            return HttpServerResponse.of(200, HttpBody.plaintext(r));
        }
    }
}
