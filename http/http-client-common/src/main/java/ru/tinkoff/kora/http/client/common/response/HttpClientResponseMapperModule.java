package ru.tinkoff.kora.http.client.common.response;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface HttpClientResponseMapperModule {
    default HttpClientResponseMapper<byte[]> byteArrayHttpClientResponseMapper() {
        return response -> response.body().asArrayStage().toCompletableFuture().join();
    }

    default HttpClientResponseMapper<ByteBuffer> byteBufferHttpClientResponseMapper() {
        return response -> response.body().asBufferStage().toCompletableFuture().join();
    }

    default HttpClientResponseMapper<CompletionStage<byte[]>> byteArrayCompletionStageHttpClientResponseMapper() {
        return response -> response.body().asArrayStage();
    }

    default HttpClientResponseMapper<CompletionStage<ByteBuffer>> byteBufferCompletionStageHttpClientResponseMapper() {
        return response -> response.body().asBufferStage();
    }

    default HttpClientResponseMapper<HttpClientResponse> noopClientResponseMapper() {
        return r -> r;
    }

    default HttpClientResponseMapper<Flow.Publisher<ByteBuffer>> byteBufferFluxHttpClientResponseMapper() {
        return HttpClientResponse::body;
    }
}
