package io.koraframework.http.server.common.request.mapper;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.http.common.form.FormUrlEncoded;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestMapper;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.Json;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface HttpServerRequestMapperModule {

    @DefaultComponent
    default HttpServerRequestMapper<HttpServerRequest> noopHttpServerRequestMapper() {
        return (r) -> r;
    }

    @DefaultComponent
    default HttpServerRequestMapper<ByteBuffer> byteBufBodyHttpServerRequestMapper() {
        return (r) -> {
            try (var body = r.body()) {
                var content = body.getFullContentIfAvailable();
                if (content != null) {
                    return content;
                }
                try (var is = body.asInputStream()) {
                    return ByteBuffer.wrap(is.readAllBytes());
                }
            }
        };
    }

    @DefaultComponent
    default HttpServerRequestMapper<byte[]> byteArrayHttpServerRequestMapper() {
        return (request) -> {
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
        };
    }

    @DefaultComponent
    default HttpServerRequestMapper<String> stringHttpServerRequestMapper(HttpServerRequestMapper<byte[]> mapper) {
        return request -> {
            var bytes = mapper.apply(request);
            if (bytes == null) {
                return null;
            }

            return new String(bytes, StandardCharsets.UTF_8);
        };
    }

    @DefaultComponent
    default HttpServerRequestMapper<InputStream> inputStreamHttpServerRequestMapper() {
        return r -> r.body().asInputStream();
    }

    @DefaultComponent
    default HttpServerRequestMapper<FormUrlEncoded> formUrlEncoderHttpServerRequestMapper() {
        return new FormUrlEncodedServerRequestMapper();
    }

    @DefaultComponent
    default FormMultipartServerRequestMapper formMultipartServerHttpServerRequestMapper() {
        return new FormMultipartServerRequestMapper();
    }

    @Tag(Json.class)
    @DefaultComponent
    default <T> JsonReaderHttpServerRequestMapper<T> jsonReaderHttpServerRequestMapper(JsonReader<T> reader) {
        return new JsonReaderHttpServerRequestMapper<>(reader);
    }
}
