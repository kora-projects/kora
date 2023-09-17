package ru.tinkoff.kora.http.client.async;

import io.netty.buffer.ByteBuf;
import org.asynchttpclient.request.body.Body;
import org.asynchttpclient.request.body.generator.FeedListener;
import org.asynchttpclient.request.body.generator.FeedableBodyGenerator;
import ru.tinkoff.kora.http.client.common.HttpClientEncoderException;
import ru.tinkoff.kora.http.common.body.HttpOutBody;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class AsyncHttpClientRequestBodyGenerator implements FeedableBodyGenerator {
    private final HttpOutBody body;
    private FeedListener listener;

    public AsyncHttpClientRequestBodyGenerator(HttpOutBody body) {
        this.body = body;
    }

    @Override
    public boolean feed(ByteBuf buffer, boolean isLast) throws Exception {
        throw new IllegalStateException("Never should be called");
    }

    @Override
    public void setListener(FeedListener listener) {
        this.listener = listener;
    }

    @Override
    public Body createBody() {
        return new PublisherBody(this);
    }

    private record Signal(ByteBuffer data, Throwable error) {
        public static final Signal LAST = new Signal(null, null);
    }

    private static class PublisherBody extends AtomicBoolean implements Body, Flow.Subscriber<ByteBuffer> {
        private final HttpOutBody body;
        private final Deque<Signal> queue = new ConcurrentLinkedDeque<>();
        private final AsyncHttpClientRequestBodyGenerator generator;
        private volatile Flow.Subscription subscription;

        private static final AtomicIntegerFieldUpdater<PublisherBody> WIP = AtomicIntegerFieldUpdater.newUpdater(PublisherBody.class, "wip");
        private volatile int wip = 0;

        public PublisherBody(AsyncHttpClientRequestBodyGenerator generator) {
            this.body = generator.body;
            this.generator = generator;
        }

        @Override
        public long getContentLength() {
            return this.body.contentLength();
        }

        @Override
        public BodyState transferTo(ByteBuf target) throws IOException {
            if (WIP.compareAndSet(this, 0, 1)) {
                if (this.compareAndSet(false, true)) {
                    this.body.subscribe(this);
                }
                try {
                    return this.readNextChunk(target);
                } finally {
                    WIP.set(this, 0);
                }
            } else {
                return BodyState.SUSPEND;
            }
        }

        public BodyState readNextChunk(ByteBuf target) {
            var res = BodyState.SUSPEND;
            var q = this.queue;
            while (target.isWritable()) {
                var nextChunk = q.peek();
                if (nextChunk == null) {
                    // Nothing in the queue. suspend stream if nothing was read. (reads == 0)
                    return res;
                } else if (nextChunk == Signal.LAST) {
                    q.remove();
                    return BodyState.STOP;
                } else if (nextChunk.error() != null) {
                    q.remove();
                    throw new HttpClientEncoderException(nextChunk.error());
                } else if (!nextChunk.data().hasRemaining()) {
                    // skip empty buffers
                    q.remove();
                    this.subscription.request(1);
                } else {
                    res = BodyState.CONTINUE;
                    target.writeBytes(nextChunk.data());
                    if (!nextChunk.data().hasRemaining()) {
                        q.remove();
                        this.subscription.request(1);
                    }
                }
            }
            return res;
        }

        @Override
        public void close() throws IOException {
            this.body.close();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            if (item.hasRemaining()) {
                this.queue.add(new Signal(item, null));// todo copy ?
                this.generator.listener.onContentAdded();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            this.generator.listener.onError(new HttpClientEncoderException(throwable));
            this.queue.add(new Signal(null, throwable));
        }

        @Override
        public void onComplete() {
            this.queue.addLast(Signal.LAST);
            this.generator.listener.onContentAdded();
        }
    }
}
