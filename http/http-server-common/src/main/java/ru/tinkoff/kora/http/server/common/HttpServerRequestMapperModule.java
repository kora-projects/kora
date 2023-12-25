package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.common.form.FormUrlEncoded;
import ru.tinkoff.kora.http.server.common.form.FormMultipartAsyncServerRequestMapper;
import ru.tinkoff.kora.http.server.common.form.FormMultipartServerRequestMapper;
import ru.tinkoff.kora.http.server.common.form.FormUrlEncodedAsyncServerRequestMapper;
import ru.tinkoff.kora.http.server.common.form.FormUrlEncodedServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface HttpServerRequestMapperModule {

    default HttpServerRequestMapper<HttpServerRequest> noopRequestMapper() {
        return (r) -> r;
    }

    default HttpServerRequestMapper<CompletionStage<HttpServerRequest>> noopAsyncRequestMapper() {
        return CompletableFuture::completedFuture;
    }

    default HttpServerRequestMapper<ByteBuffer> byteBufBodyRequestMapper() {
        return (r) -> {
            final ByteBuffer content = r.body().getFullContentIfAvailable();
            return content != null
                ? content
                : r.body().asBufferStage().toCompletableFuture().join();
        };
    }

    default HttpServerRequestMapper<CompletionStage<ByteBuffer>> byteBufAsyncBodyRequestMapper() {
        return (r) -> {
            final ByteBuffer content = r.body().getFullContentIfAvailable();
            return content != null
                ? CompletableFuture.completedFuture(content)
                : r.body().asBufferStage();
        };
    }

    default HttpServerRequestMapper<CompletionStage<byte[]>> byteArrayAsyncRequestMapper() {
        return (request) -> {
            var full = request.body().getFullContentIfAvailable();
            if (full != null) {
                if (full.hasArray() && full.arrayOffset() == 0 && full.array().length == full.remaining()) {
                    return CompletableFuture.completedFuture(full.array());
                }
                var array = new byte[full.remaining()];
                full.get(array);
                return CompletableFuture.completedFuture(array);
            }
            return request.body().asArrayStage();
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

    default HttpServerRequestMapper<CompletionStage<String>> stringAsyncRequestMapper(HttpServerRequestMapper<CompletionStage<byte[]>> mapper) {
        return (request) -> {
            var full = request.body().getFullContentIfAvailable();
            if (full != null) {
                if (full.hasArray() && full.arrayOffset() == 0 && full.array().length == full.remaining()) {
                    return CompletableFuture.completedFuture(new String(full.array(), StandardCharsets.UTF_8));
                }
                var array = new byte[full.remaining()];
                full.get(array);
                return CompletableFuture.completedFuture(new String(array, StandardCharsets.UTF_8));
            }

            return FlowUtils.toByteArrayFuture(request.body())
                    .thenApply(bytes -> {
                        if(bytes == null) {
                            return null;
                        }

                        return new String(bytes, StandardCharsets.UTF_8);
                    });
        };
    }

    default HttpServerRequestMapper<String> stringRequestMapper(HttpServerRequestMapper<byte[]> mapper) {
        return request -> {
            final byte[] bytes = mapper.apply(request);
            if(bytes == null) {
                return null;
            }

            return new String(bytes, StandardCharsets.UTF_8);
        };
    }

    default HttpServerRequestMapper<Flow.Publisher<ByteBuffer>> byteBufferPublisherRequestMapper() {
        return HttpServerRequest::body;
    }

    default HttpServerRequestMapper<CompletionStage<Flow.Publisher<ByteBuffer>>> byteBufferPublisherAsyncRequestMapper() {
        return (request) -> CompletableFuture.completedFuture(request.body());
    }

    default HttpServerRequestMapper<InputStream> inputStreamRequestMapper() {
        return r -> r.body().asInputStream();
    }

    default HttpServerRequestMapper<FormUrlEncoded> formUrlEncoderHttpServerRequestMapper() {
        return new FormUrlEncodedServerRequestMapper();
    }

    default HttpServerRequestMapper<CompletionStage<FormUrlEncoded>> formUrlEncodedAsyncServerRequestMapper() {
        return new FormUrlEncodedAsyncServerRequestMapper();
    }

    default FormMultipartAsyncServerRequestMapper formMultipartAsyncServerRequestMapper() {
        return new FormMultipartAsyncServerRequestMapper();
    }

    default FormMultipartServerRequestMapper formMultipartServerRequestMapper() {
        return new FormMultipartServerRequestMapper();
    }
}
