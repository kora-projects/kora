package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.http.client.common.form.FormMultipartClientRequestMapper;
import ru.tinkoff.kora.http.client.common.form.FormUrlEncodedClientRequestMapper;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public interface HttpClientRequestMapperModule {
    default HttpClientRequestMapper<byte[]> byteArrayHttpClientRequestMapper() {
        return (ctx, body) -> HttpBody.octetStream(body);
    }

    default HttpClientRequestMapper<ByteBuffer> byteBufferHttpClientRequestMapper() {
        return (ctx, body) -> HttpBody.octetStream(body);
    }

    default HttpClientRequestMapper<Flow.Publisher<ByteBuffer>> byteBufferPublisherHttpClientRequestMapper() {
        return (ctx, body) -> HttpBodyOutput.octetStream(body);
    }

    default FormUrlEncodedClientRequestMapper formUrlEncodedClientRequestMapper() {
        return new FormUrlEncodedClientRequestMapper();
    }

    default FormMultipartClientRequestMapper formMultipartClientRequestMapper() {
        return new FormMultipartClientRequestMapper();
    }
}
