package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.flow.EmptySubscription;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public final class EmptyHttpBody implements HttpBodyInput, HttpBodyOutput {

    public final static EmptyHttpBody INSTANCE = new EmptyHttpBody();

    private static final byte[] emptyArray = new byte[0];
    private static final ByteBuffer emptyBuffer = ByteBuffer.wrap(emptyArray);

    private EmptyHttpBody() { }

    @Override
    public ByteBuffer getFullContentIfAvailable() {
        return emptyBuffer;
    }

    @Override
    public long contentLength() {
        return 0;
    }

    @Nullable
    @Override
    public String contentType() {
        return null;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new EmptySubscription<>(Context.current(), subscriber));
    }

    @Override
    public CompletionStage<ByteBuffer> asBufferStage() {
        return CompletableFuture.completedFuture(emptyBuffer);
    }

    @Override
    public CompletionStage<byte[]> asArrayStage() {
        return CompletableFuture.completedFuture(emptyArray);
    }

    @Override
    public void write(OutputStream os) throws IOException {
    }

    @Override
    public InputStream asInputStream() {
        return InputStream.nullInputStream();
    }

    @Override
    public void close() {

    }
}
