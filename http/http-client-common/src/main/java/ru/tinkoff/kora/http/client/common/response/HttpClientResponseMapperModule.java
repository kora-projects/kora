package ru.tinkoff.kora.http.client.common.response;

import kotlin.Unit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.util.ReactorUtils;

import java.nio.ByteBuffer;

public interface HttpClientResponseMapperModule {
    default HttpClientResponseMapper<byte[], Mono<byte[]>> byteArrayHttpClientResponseMapper() {
        return response -> ReactorUtils.toByteArrayMono(response.body());
    }

    default HttpClientResponseMapper<ByteBuffer, Mono<ByteBuffer>> byteBufferHttpClientResponseMapper() {
        return response -> ReactorUtils.toByteBufferMono(response.body());
    }

    default HttpClientResponseMapper<HttpClientResponse, Mono<HttpClientResponse>> noopClientResponseMapper() {
        return Mono::just;
    }

    default HttpClientResponseMapper<ByteBuffer, Flux<ByteBuffer>> byteBufferFluxHttpClientResponseMapper() {
        return HttpClientResponse::body;
    }

    default HttpClientResponseMapper<Unit, Mono<Unit>> unitHttpClientResponseMapper() {
        return response -> response.close().thenReturn(Unit.INSTANCE);
    }

    default HttpClientResponseMapper<Void, Mono<Void>> voidHttpClientResponseMapper() {
        return HttpClientResponse::close;
    }

}
