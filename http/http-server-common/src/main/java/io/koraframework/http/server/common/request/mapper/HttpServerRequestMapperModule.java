package io.koraframework.http.server.common.request.mapper;

import io.koraframework.common.annotation.*;
import io.koraframework.http.common.body.*;
import io.koraframework.http.common.form.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

public interface HttpServerRequestMapperModule {

    @DefaultComponent
    default HttpServerRequestMapper<HttpServerRequest> noopHttpServerRequestMapper() {
        return new NoopRequestMapper();
    }

    @DefaultComponent
    default HttpServerRequestMapper<HttpBodyInput> httpBodyInputHttpServerRequestMapper() {
        return new HttpBodyInputRequestMapper();
    }

    @DefaultComponent
    default HttpServerRequestMapper<ByteBuffer> byteBufferHttpServerRequestMapper() {
        return new ByteBufferRequestMapper();
    }

    @DefaultComponent
    default HttpServerRequestMapper<byte[]> byteArrayHttpServerRequestMapper() {
        return new ByteArrayRequestMapper();
    }

    @DefaultComponent
    default HttpServerRequestMapper<String> stringHttpServerRequestMapper(HttpServerRequestMapper<byte[]> mapper) {
        return new StringRequestMapper(mapper);
    }

    @DefaultComponent
    default HttpServerRequestMapper<InputStream> inputStreamHttpServerRequestMapper() {
        return new InputStreamRequestMapper();
    }

    @DefaultComponent
    default HttpServerRequestMapper<FormUrlEncoded> formUrlEncodedHttpServerRequestMapper() {
        return new FormUrlEncodedServerRequestMapper();
    }

    @DefaultComponent
    default HttpServerRequestMapper<FormMultipart> formMultipartHttpServerRequestMapper() {
        return new FormMultipartServerRequestMapper();
    }

    @Json
    @DefaultComponent
    default <T> HttpServerRequestMapper<T> jsonHttpServerRequestMapper(JsonReader<T> reader) {
        return new JsonReaderHttpServerRequestMapper<>(reader);
    }

    final class NoopRequestMapper implements HttpServerRequestMapper<HttpServerRequest> {
        @Override
        public HttpServerRequest apply(HttpServerRequest r) throws Exception {
            return r;
        }
    }

    final class HttpBodyInputRequestMapper implements HttpServerRequestMapper<HttpBodyInput> {
        @Override
        public HttpBodyInput apply(HttpServerRequest r) throws Exception {
            return r.body();
        }
    }

    final class ByteBufferRequestMapper implements HttpServerRequestMapper<ByteBuffer> {
        @Override
        public ByteBuffer apply(HttpServerRequest r) throws Exception {
            try (var body = r.body()) {
                var content = body.getFullContentIfAvailable();
                if (content != null) {
                    return content;
                }
                try (var is = body.asInputStream()) {
                    return ByteBuffer.wrap(is.readAllBytes());
                }
            }
        }
    }

    final class ByteArrayRequestMapper implements HttpServerRequestMapper<byte[]> {
        @Override
        public byte[] apply(HttpServerRequest request) throws Exception {
            try (var body = request.body()) {
                var full = body.getFullContentIfAvailable();
                if (full != null) {
                    if (full.hasArray() && full.arrayOffset() == 0 && full.array().length == full.remaining()) {
                        return full.array();
                    }
                    var array = new byte[full.remaining()];
                    full.get(array);
                    return array;
                }

                try (var is = body.asInputStream()) {
                    return is.readAllBytes();
                }
            }
        }
    }

    final class StringRequestMapper implements HttpServerRequestMapper<String> {
        private final HttpServerRequestMapper<byte[]> mapper;

        public StringRequestMapper(HttpServerRequestMapper<byte[]> mapper) {
            this.mapper = mapper;
        }

        @Override
        public String apply(HttpServerRequest request) throws Exception {
            var bytes = mapper.apply(request);
            if (bytes == null) {
                return null;
            }

            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    final class InputStreamRequestMapper implements HttpServerRequestMapper<InputStream> {
        @Override
        public InputStream apply(HttpServerRequest r) throws Exception {
            return r.body().asInputStream();
        }
    }
}
