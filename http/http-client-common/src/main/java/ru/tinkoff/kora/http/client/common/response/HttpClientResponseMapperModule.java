package ru.tinkoff.kora.http.client.common.response;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface HttpClientResponseMapperModule {
    default HttpClientResponseMapper<byte[]> byteArrayHttpClientResponseMapper() {
        return response -> response.body().collectArray().toCompletableFuture().join();
    }

    default HttpClientResponseMapper<ByteBuffer> byteBufferHttpClientResponseMapper() {
        return response -> response.body().collectBuf().toCompletableFuture().join();
    }

    default HttpClientResponseMapper<CompletionStage<byte[]>> byteArrayCompletionStageHttpClientResponseMapper() {
        return response -> response.body().collectArray();
    }

    default HttpClientResponseMapper<CompletionStage<ByteBuffer>> byteBufferCompletionStageHttpClientResponseMapper() {
        return response -> response.body().collectBuf();
    }

    default HttpClientResponseMapper<HttpClientResponse> noopClientResponseMapper() {
        return r -> r;
    }

    default HttpClientResponseMapper<Flow.Publisher<ByteBuffer>> byteBufferFluxHttpClientResponseMapper() {
        return HttpClientResponse::body;
    }
}
