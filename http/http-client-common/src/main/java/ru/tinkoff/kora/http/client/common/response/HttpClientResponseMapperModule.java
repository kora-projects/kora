package ru.tinkoff.kora.http.client.common.response;

import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.common.body.HttpBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface HttpClientResponseMapperModule {

    default HttpClientResponseMapper<String> stringHttpClientResponseMapper() {
        return HttpClientResponseMapper.fromAsync(response -> response.body().asArrayStage().thenApply(r -> new String(r, StandardCharsets.UTF_8)));
    }

    default HttpClientResponseMapper<CompletionStage<String>> stringCompletionStageHttpClientResponseMapper() {
        return response -> response.body().asArrayStage().thenApply(r -> new String(r, StandardCharsets.UTF_8));
    }

    default HttpClientResponseMapper<byte[]> byteArrayHttpClientResponseMapper() {
        return HttpClientResponseMapper.fromAsync(response -> response.body().asArrayStage());
    }

    default HttpClientResponseMapper<CompletionStage<byte[]>> byteArrayCompletionStageHttpClientResponseMapper() {
        return response -> response.body().asArrayStage();
    }

    default HttpClientResponseMapper<ByteBuffer> byteBufferHttpClientResponseMapper() {
        return HttpClientResponseMapper.fromAsync(response -> response.body().asBufferStage());
    }

    default HttpClientResponseMapper<CompletionStage<ByteBuffer>> byteBufferCompletionStageHttpClientResponseMapper() {
        return response -> response.body().asBufferStage();
    }

    default <T> HttpClientResponseMapper<HttpResponseEntity<T>> entityResponseHttpClientResponseMapper(HttpClientResponseMapper<T> mapper) {
        return response -> HttpResponseEntity.of(response.code(), response.headers().toMutable(), mapper.apply(response));
    }

    default <T> HttpClientResponseMapper<CompletionStage<HttpResponseEntity<T>>> entityResponseCompletionStageHttpClientResponseMapper(HttpClientResponseMapper<T> mapper) {
        return response -> response.body().asArrayStage().thenApply(body -> {
            try {
                var temp = new HttpClientResponse.Default(response.code(), response.headers(), HttpBody.of(body), () -> { });
                return HttpResponseEntity.of(temp.code(), temp.headers().toMutable(), mapper.apply(temp));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    default HttpClientResponseMapper<Flow.Publisher<ByteBuffer>> byteBufferFluxHttpClientResponseMapper() {
        return HttpClientResponse::body;
    }
}
