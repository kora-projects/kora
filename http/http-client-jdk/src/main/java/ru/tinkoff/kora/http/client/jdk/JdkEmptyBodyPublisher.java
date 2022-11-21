package ru.tinkoff.kora.http.client.jdk;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public class JdkEmptyBodyPublisher implements HttpRequest.BodyPublisher {

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new JdbEmptyBodySubscription(subscriber));
    }

    @Override
    public long contentLength() {
        return 0;
    }

    private static final class JdbEmptyBodySubscription implements Flow.Subscription {
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final Flow.Subscriber<? super ByteBuffer> subscriber;

        private JdbEmptyBodySubscription(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n > 0 && this.completed.compareAndSet(false, true)) {
                this.subscriber.onComplete();
            }
        }

        @Override
        public void cancel() {

        }
    }

}
