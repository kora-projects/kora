package ru.tinkoff.kora.http.client.jdk;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class JdkHttpClientResponse implements HttpClientResponse {
    private final HttpResponse<Flow.Publisher<List<ByteBuffer>>> response;
    private final JdkHttpClientHeaders headers;
    private final BodyPublisher body;

    public JdkHttpClientResponse(HttpResponse<Flow.Publisher<List<ByteBuffer>>> response) {
        this.response = response;
        this.headers = new JdkHttpClientHeaders(this.response.headers());
        this.body = new BodyPublisher(response);
    }

    @Override
    public int code() {
        return this.response.statusCode();
    }

    @Override
    public HttpHeaders headers() {
        return this.headers;
    }

    @Override
    public HttpBodyInput body() {
        return this.body;
    }

    @Override
    public void close() throws IOException {
        this.body.close();
    }

    @Override
    public String toString() {
        return "HttpClientResponse{code=" + code() +
               ", bodyLength=" + body.contentLength() +
               ", bodyType=" + body.contentType() +
               '}';
    }

    private static final class BodyPublisher extends AtomicBoolean implements HttpBodyInput {
        private static final String EMPTY = "";
        private final Flow.Publisher<List<ByteBuffer>> publisher;
        private final java.net.http.HttpHeaders headers;
        private volatile HttpResponseInputStream is;
        private volatile long contentLength = -2;
        private volatile String contentType;

        public BodyPublisher(HttpResponse<Flow.Publisher<List<ByteBuffer>>> response) {
            this.publisher = response.body();
            this.headers = response.headers();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            if (this.compareAndSet(false, true)) {
                this.publisher.subscribe(new ListSubscriber(subscriber));
            } else {
                throw new IllegalStateException("Publishers was already subscribed");
            }
        }

        @Override
        public long contentLength() {
            var contentLength = this.contentLength;
            if (contentLength == -2) {
                this.contentLength = contentLength = headers.firstValueAsLong("content-length").orElse(-1);
            }
            return contentLength;
        }

        @Nullable
        @Override
        public String contentType() {
            var contentType = this.contentType;
            if (Objects.equals(contentType, EMPTY)) {
                this.contentType = contentType = headers.firstValue("content-type").orElse(null);
            }
            return contentType;
        }

        @Override
        public InputStream asInputStream() {
            var is = this.is;
            if (is == null) {
                if (this.compareAndSet(false, true)) {
                    this.is = is = new HttpResponseInputStream();
                    this.publisher.subscribe(is);
                } else {
                    throw new IllegalStateException("Publishers was already subscribed");
                }
            }
            return is;
        }


        @Override
        public void close() throws IOException {
            if (this.compareAndSet(false, true)) {
                this.publisher.subscribe(FlowUtils.drain());
            }
        }
    }

    private static class ListSubscriber implements Flow.Subscriber<List<ByteBuffer>> {
        private static final AtomicLongFieldUpdater<ListSubscriber> DEMAND = AtomicLongFieldUpdater.newUpdater(ListSubscriber.class, "demand");
        private volatile long demand = 0;

        private static final AtomicIntegerFieldUpdater<ListSubscriber> WIP = AtomicIntegerFieldUpdater.newUpdater(ListSubscriber.class, "wip");
        private volatile int wip = 0;

        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final BlockingQueue<ByteBuffer> buffer;
        private volatile Flow.Subscription subscription;
        private volatile boolean completed = false;

        public ListSubscriber(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
            buffer = new ArrayBlockingQueue<>(16);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            var s = new Flow.Subscription() {
                @Override
                public void request(long n) {
                    synchronized (subscriber) {
                        DEMAND.getAndAdd(ListSubscriber.this, n);
                        if (WIP.compareAndSet(ListSubscriber.this, 0, 1)) {
                            ListSubscriber.this.drain();
                        }
                    }
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                }
            };
            this.subscription = subscription;
            this.subscriber.onSubscribe(s);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            if (WIP.compareAndSet(this, 0, 1)) {
                for (var byteBuffer : item) {
                    if (this.buffer.isEmpty()) {
                        // short path
                        var oldDemand = DEMAND.getAndDecrement(this);
                        if (oldDemand > 0) {
                            this.subscriber.onNext(byteBuffer);
                        } else {
                            DEMAND.incrementAndGet(this);
                            var bb = ByteBuffer.allocate(byteBuffer.remaining());
                            bb.put(byteBuffer);
                            bb.rewind();
                            this.buffer.add(bb);
                        }
                    } else {
                        var bb = ByteBuffer.allocate(byteBuffer.remaining());
                        bb.put(byteBuffer);
                        bb.rewind();
                        this.buffer.add(bb);
                    }
                }
                this.drain();
            } else {
                for (var byteBuffer : item) {
                    var bb = ByteBuffer.allocate(byteBuffer.remaining());
                    bb.put(byteBuffer);
                    bb.rewind();
                    this.buffer.add(bb);
                }
                if (WIP.compareAndSet(this, 0, 1)) {
                    this.drain();
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            completed = true;
            if (WIP.compareAndSet(this, 0, 1)) {
                this.drain();
            }
        }

        private void drain() {
            while (true) {
                var item = buffer.peek();
                if (item == null) {
                    if (completed) {
                        this.subscriber.onComplete();
                        WIP.set(this, 0);
                        return;
                    } else {
                        this.subscription.request(1);
                        item = buffer.peek();
                    }
                }
                if (item == null) {
                    if (completed) {
                        this.subscriber.onComplete();
                    }
                    WIP.set(this, 0);
                    return;
                }
                var oldDemand = DEMAND.getAndDecrement(this);
                if (oldDemand <= 0) {
                    DEMAND.incrementAndGet(this);
                    WIP.set(this, 0);
                    return;
                }
                buffer.remove();
                subscriber.onNext(item);
            }
        }
    }

}
