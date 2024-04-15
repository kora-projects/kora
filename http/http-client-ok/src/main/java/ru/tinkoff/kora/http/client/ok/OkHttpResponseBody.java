package ru.tinkoff.kora.http.client.ok;

import jakarta.annotation.Nullable;
import okhttp3.ResponseBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public final class OkHttpResponseBody implements HttpBodyInput {
    private final ResponseBody body;

    public OkHttpResponseBody(ResponseBody body) {
        this.body = body;
    }

    @Override
    public long contentLength() {
        return (int) body.contentLength();
    }

    @Nullable
    @Override
    public String contentType() {
        var ct = body.contentType();
        if (ct == null) {
            return null;
        }
        return ct.toString();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new Subscription(body, subscriber));
    }

    @Override
    public InputStream asInputStream() {
        return this.body.byteStream();
    }

    @Override
    public CompletionStage<ByteBuffer> asBufferStage() {
        try {
            return CompletableFuture.completedFuture(
                this.body.byteString().asByteBuffer()
            );
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletionStage<byte[]> asArrayStage() {
        try {
            return CompletableFuture.completedFuture(
                this.body.bytes()
            );
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void close() throws IOException {
        this.body.close();
    }

    private static final class Subscription implements Flow.Subscription {
        private final ResponseBody body;
        private final Flow.Subscriber<? super ByteBuffer> subscriber;

        private Subscription(ResponseBody body, Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.body = body;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            var buf = new byte[1024];
            var is = this.body.byteStream();
            while (n > 0) {
                n--;
                var len = 0;
                try {
                    len = is.read(buf);
                } catch (IOException io) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        io.addSuppressed(e);
                    }
                    this.subscriber.onError(io);
                    return;
                }
                if (len >= 0) {
                    this.subscriber.onNext(ByteBuffer.allocate(len)
                        .put(buf, 0, len)
                        .rewind());
                } else {
                    break;
                }
            }
            this.subscriber.onComplete();
        }

        @Override
        public void cancel() {
            this.body.close();
        }
    }
}
