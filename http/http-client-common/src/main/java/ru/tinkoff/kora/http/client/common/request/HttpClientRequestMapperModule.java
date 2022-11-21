package ru.tinkoff.kora.http.client.common.request;

import java.nio.ByteBuffer;

public interface HttpClientRequestMapperModule {
    default HttpClientRequestMapper<byte[]> byteArrayHttpClientRequestMapper() {
        return request -> request.builder().body(request.parameter());
    }

    default HttpClientRequestMapper<ByteBuffer> byteBufferHttpClientRequestMapper() {
        return request -> request.builder().body(request.parameter());
    }

    default HttpClientRequestMapper<HttpClientRequest> noopClientRequestMapper() {
        return request -> request.builder();
    }
}
