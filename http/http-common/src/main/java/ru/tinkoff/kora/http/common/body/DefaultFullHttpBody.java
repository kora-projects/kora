package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.common.util.flow.SingleSubscription;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public final class DefaultFullHttpBody implements HttpBodyInput, HttpBodyOutput {
    private final Context context;
    private final ByteBuffer data;
    private final String contentType;

    public DefaultFullHttpBody(Context context, ByteBuffer data, @Nullable String contentType) {
        this.context = context;
        this.data = data;
        this.contentType = contentType;
    }

    @Override
    public int contentLength() {
        return data.remaining();
    }

    @Nullable
    @Override
    public String contentType() {
        return this.contentType;
    }

    @Override
    public ByteBuffer getFullContentIfAvailable() {
        return this.data.slice();
    }

    @Override
    public CompletionStage<ByteBuffer> asBufferStage() {
        return CompletableFuture.completedFuture(data.slice());
    }

    @Override
    public CompletionStage<byte[]> asArrayStage() {
        if (data.hasArray() && data.arrayOffset() == 0 && data.remaining() == data.array().length) {
            return CompletableFuture.completedFuture(data.array());
        }
        var array = new byte[data.remaining()];
        data.slice().get(array);
        return CompletableFuture.completedFuture(array);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new SingleSubscription<>(subscriber, this.context, this.data.slice()));
    }

    @Override
    public void write(OutputStream os) throws IOException {
        var data = this.data;
        if (data.hasArray()) {
            os.write(data.array(), data.arrayOffset(), data.remaining());
        } else {
            var buf = new byte[1024];
            data = data.slice();
            while (data.hasRemaining()) {
                var len = Math.min(data.remaining(), buf.length);
                data.get(buf);
                os.write(buf, 0, len);
            }
        }
    }

    @Override
    public InputStream asInputStream() {
        return new ByteBufferInputStream(this.data.slice());
    }

    @Override
    public void close() {

    }
}
