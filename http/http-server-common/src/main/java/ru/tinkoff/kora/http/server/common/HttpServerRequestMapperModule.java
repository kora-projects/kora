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
        return (r) -> r.body().getFullContentIfAvailable() != null
            ? r.body().getFullContentIfAvailable()
            : r.body().collectBuf().toCompletableFuture().join();
    }

    default HttpServerRequestMapper<CompletionStage<ByteBuffer>> byteBufAsyncBodyRequestMapper() {
        return (r) -> r.body().getFullContentIfAvailable() != null
            ? CompletableFuture.completedFuture(r.body().getFullContentIfAvailable())
            : r.body().collectBuf();
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
            return FlowUtils.toByteArrayFuture(request.body());
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
            return FlowUtils.toByteArrayFuture(request.body()).join();
        };
    }

    default HttpServerRequestMapper<Flow.Publisher<ByteBuffer>> byteBufferPublisherRequestMapper() {
        return HttpServerRequest::body;
    }

    default HttpServerRequestMapper<CompletionStage<Flow.Publisher<ByteBuffer>>> byteBufferPublisherAsyncRequestMapper() {
        return (request) -> CompletableFuture.completedFuture(request.body());
    }

    default HttpServerRequestMapper<InputStream> inputStreamRequestMapper() {
        return r -> r.body().getInputStream();
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
