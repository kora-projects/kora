package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.http.client.common.form.FormMultipartClientRequestMapper;
import ru.tinkoff.kora.http.client.common.form.FormUrlEncodedClientRequestMapper;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public interface HttpClientRequestMapperModule {
    default HttpClientRequestMapper<byte[]> byteArrayHttpClientRequestMapper() {
        return (ctx, builder, body) -> builder.body(body);
    }

    default HttpClientRequestMapper<ByteBuffer> byteBufferHttpClientRequestMapper() {
        return (ctx, builder, body) -> builder.body(body);
    }

    default HttpClientRequestMapper<Flow.Publisher<ByteBuffer>> byteBufferPublisherHttpClientRequestMapper() {
        return (ctx, builder, body) -> builder.body(body);
    }

    default FormUrlEncodedClientRequestMapper formUrlEncodedClientRequestMapper() {
        return new FormUrlEncodedClientRequestMapper();
    }

    default FormMultipartClientRequestMapper formMultipartClientRequestMapper() {
        return new FormMultipartClientRequestMapper();
    }
}
