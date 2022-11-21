package ru.tinkoff.kora.http.client.jdk;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public class JdkByteBufferBodyPublisher implements HttpRequest.BodyPublisher {
    private final ByteBuffer content;

    public JdkByteBufferBodyPublisher(ByteBuffer content) {
        this.content = content;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new JdbByteBufferBodySubscription(subscriber, this.content));
    }

    @Override
    public long contentLength() {
        return this.content.remaining();
    }

    private static final class JdbByteBufferBodySubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final ByteBuffer content;
        private final AtomicBoolean completed = new AtomicBoolean(false);

        private JdbByteBufferBodySubscription(Flow.Subscriber<? super ByteBuffer> subscriber, ByteBuffer content) {
            this.subscriber = subscriber;
            this.content = content;
        }

        @Override
        public void request(long n) {
            if (n > 0 && this.completed.compareAndSet(false, true)) {
                try {
                    this.subscriber.onNext(this.content);
                } catch (Exception e) {
                    this.subscriber.onError(e);
                    return;
                }
                this.subscriber.onComplete();
            }
        }

        @Override
        public void cancel() {

        }
    }

}
