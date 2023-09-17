package ru.tinkoff.kora.http.common.body;

import ru.tinkoff.kora.common.util.ByteBufferPublisherInputStream;
import ru.tinkoff.kora.common.util.FlowUtils;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface HttpInBody extends HttpBody, Flow.Publisher<ByteBuffer> {
    @Override
    void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber);

    default InputStream getInputStream() {
        return new ByteBufferPublisherInputStream(this);
    }

    default CompletionStage<ByteBuffer> collectBuf() {
        return FlowUtils.toByteBufferFuture(this);
    }

    default CompletionStage<byte[]> collectArray() {
        return FlowUtils.toByteArrayFuture(this);
    }
}
