package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.ByteBufferPublisherInputStream;
import ru.tinkoff.kora.common.util.FlowUtils;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface HttpBodyInput extends HttpBody, Flow.Publisher<ByteBuffer> {
    @Override
    void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber);

    @Nullable
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
