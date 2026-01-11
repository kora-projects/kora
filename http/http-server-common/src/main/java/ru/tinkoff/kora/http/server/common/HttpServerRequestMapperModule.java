package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.common.form.FormUrlEncoded;
import ru.tinkoff.kora.http.server.common.form.FormMultipartServerRequestMapper;
import ru.tinkoff.kora.http.server.common.form.FormUrlEncodedServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.mapper.JsonReaderHttpServerRequestMapper;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface HttpServerRequestMapperModule {

    @DefaultComponent
    default HttpServerRequestMapper<HttpServerRequest> noopRequestMapper() {
        return (r) -> r;
    }

    @DefaultComponent
    default HttpServerRequestMapper<ByteBuffer> byteBufBodyRequestMapper() {
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
    default HttpServerRequestMapper<byte[]> byteArrayRequestMapper() {
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
    default HttpServerRequestMapper<String> stringRequestMapper(HttpServerRequestMapper<byte[]> mapper) {
        return request -> {
            var bytes = mapper.apply(request);
            if (bytes == null) {
                return null;
            }

            return new String(bytes, StandardCharsets.UTF_8);
        };
    }

    @DefaultComponent
    default HttpServerRequestMapper<InputStream> inputStreamRequestMapper() {
        return r -> r.body().asInputStream();
    }

    @DefaultComponent
    default HttpServerRequestMapper<FormUrlEncoded> formUrlEncoderHttpServerRequestMapper() {
        return new FormUrlEncodedServerRequestMapper();
    }

    @DefaultComponent
    default FormMultipartServerRequestMapper formMultipartServerRequestMapper() {
        return new FormMultipartServerRequestMapper();
    }

    @Tag(Json.class)
    @DefaultComponent
    default <T> JsonReaderHttpServerRequestMapper<T> jsonReaderHttpServerRequestMapper(JsonReader<T> reader) {
        return new JsonReaderHttpServerRequestMapper<>(reader);
    }
}
