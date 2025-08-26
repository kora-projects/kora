package ru.tinkoff.kora.http.common.body;

import ru.tinkoff.kora.common.util.ByteBufferPublisherInputStream;
import ru.tinkoff.kora.common.util.FlowUtils;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * <b>Русский</b>: Описывает тело HTTP запроса
 * <hr>
 * <b>English</b>: Describes HTTP request body
 */
public interface HttpBodyInput extends HttpBody, Flow.Publisher<ByteBuffer> {
    @Override
    void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber);

    default InputStream asInputStream() {
        return new ByteBufferPublisherInputStream(this);
    }

    default CompletionStage<ByteBuffer> asBufferStage() {
        return FlowUtils.toByteBufferFuture(this);
    }

    default CompletionStage<byte[]> asArrayStage() {
        return FlowUtils.toByteArrayFuture(this);
    }
}
