package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.http.common.form.FormUrlEncoded;
import ru.tinkoff.kora.http.server.common.form.FormMultipartServerRequestMapper;
import ru.tinkoff.kora.http.server.common.form.FormUrlEncodedServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface HttpServerRequestMapperModule {

    default HttpServerRequestMapper<HttpServerRequest> noopRequestMapper() {
        return (r) -> r;
    }

    default HttpServerRequestMapper<ByteBuffer> byteBufBodyRequestMapper() {
        return (r) -> {
            final ByteBuffer content = r.body().getFullContentIfAvailable();
            return content != null
                ? content
                : r.body().asBufferStage().toCompletableFuture().join();
        };
    }

    default HttpServerRequestMapper<byte[]> byteArrayRequestMapper() {
        return (request) -> {
            var full = request.body().getFullContentIfAvailable();
            if (full != null) {
                if (full.hasArray() && full.arrayOffset() == 0 && full.array().length == full.remaining()) {
                    return full.array();
                }
                var array = new byte[full.remaining()];
                full.get(array);
                return array;
            }

            try (var is = request.body().asInputStream()) {
                if (is != null) {
                    return is.readAllBytes();
                }
            }

            return request.body().asArrayStage().toCompletableFuture().join();
        };
    }

    default HttpServerRequestMapper<String> stringRequestMapper(HttpServerRequestMapper<byte[]> mapper) {
        return request -> {
            final byte[] bytes = mapper.apply(request);
            if (bytes == null) {
                return null;
            }

            return new String(bytes, StandardCharsets.UTF_8);
        };
    }

    default HttpServerRequestMapper<InputStream> inputStreamRequestMapper() {
        return r -> r.body().asInputStream();
    }

    default HttpServerRequestMapper<FormUrlEncoded> formUrlEncoderHttpServerRequestMapper() {
        return new FormUrlEncodedServerRequestMapper();
    }

    default FormMultipartServerRequestMapper formMultipartServerRequestMapper() {
        return new FormMultipartServerRequestMapper();
    }
}
