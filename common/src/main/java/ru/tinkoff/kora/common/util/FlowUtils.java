package ru.tinkoff.kora.common.util;


import ru.tinkoff.kora.common.Context;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlowUtils {
    public static <T> Publisher<T> empty(Context context) {
        return subscriber -> {
            var s = new EmptySubscription<T>(context, subscriber);
            subscriber.onSubscribe(s);
        };
    }

    public static <T> Publisher<T> one(Context context, T value) {
        return subscriber -> {
            var s = new SingleSubscription<>(subscriber, context, value);
            subscriber.onSubscribe(s);
        };
    }

    public static <T> Publisher<T> fromCallable(Context context, Callable<T> value) {
        return subscriber -> {
            var s = new LazySingleSubscription<>(subscriber, context, value);
            subscriber.onSubscribe(s);
        };
    }

    public static <T> Publisher<T> error(Context context, Throwable error) {
        return subscriber -> {
            var s = new ErrorSubscription<>(subscriber, context, error);
            subscriber.onSubscribe(s);
        };
    }

    public static final class EmptySubscription<T> extends AtomicBoolean implements Subscription {
        private final Context context;
        private final Subscriber<? super T> subscriber;

        public EmptySubscription(Context context, Subscriber<? super T> subscriber) {
            this.context = context;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            assert n > 0;
            if (this.compareAndSet(false, true)) {
                var subscriber = this.subscriber;
                var ctx = Context.current();
                this.context.inject();
                try {
                    subscriber.onComplete();
                } finally {
                    ctx.inject();
                }
            }
        }

        @Override
        public void cancel() {
            this.set(true);
        }
    }

    public static final class ErrorSubscription<T> extends AtomicBoolean implements Subscription {
        private final Subscriber<? super T> subscriber;
        private final Context context;
        private final Throwable error;

        public ErrorSubscription(Subscriber<? super T> subscriber, Context context, Throwable error) {
            this.subscriber = subscriber;
            this.context = context;
            this.error = error;
        }

        @Override
        public void request(long n) {
            assert n > 0;
            if (this.compareAndSet(false, true)) {
                var ctx = Context.current();
                this.context.inject();
                try {
                    this.subscriber.onError(this.error);
                } finally {
                    ctx.inject();
                }
            }
        }

        @Override
        public void cancel() {
            this.set(true);
        }
    }

    public static final class SingleSubscription<T> extends AtomicBoolean implements Subscription {
        private final Subscriber<? super T> subscriber;
        private final Context context;
        private final T value;

        public SingleSubscription(Subscriber<? super T> subscriber, Context context, T value) {
            this.subscriber = subscriber;
            this.context = context;
            this.value = value;
        }

        @Override
        public void request(long n) {
            assert n > 0;
            if (this.compareAndSet(false, true)) {
                var subscriber = this.subscriber;
                var ctx = Context.current();
                this.context.inject();
                try {
                    subscriber.onNext(this.value);
                    subscriber.onComplete();
                } finally {
                    ctx.inject();
                }
            }
        }

        @Override
        public void cancel() {
            this.set(true);
        }
    }

    public static final class LazySingleSubscription<T> extends AtomicBoolean implements Subscription {
        private final Subscriber<? super T> subscriber;
        private final Context context;
        private final Callable<? extends T> value;

        public LazySingleSubscription(Subscriber<? super T> subscriber, Context context, Callable<? extends T> value) {
            this.subscriber = subscriber;
            this.context = context;
            this.value = value;
        }

        @Override
        public void request(long n) {
            assert n > 0;
            if (this.compareAndSet(false, true)) {
                var subscriber = this.subscriber;
                var ctx = Context.current();
                this.context.inject();
                try {
                    final T value;
                    try {
                        value = this.value.call();
                    } catch (Throwable e) {
                        subscriber.onError(e);
                        return;
                    }
                    subscriber.onNext(value);
                    subscriber.onComplete();
                } finally {
                    ctx.inject();
                }
            }
        }

        @Override
        public void cancel() {
            this.set(true);
        }
    }

    public static CompletableFuture<byte[]> toByteArrayFuture(Publisher<? extends ByteBuffer> publisher) {
        return toByteArrayFuture(publisher, Integer.MAX_VALUE);
    }

    public static CompletableFuture<byte[]> toByteArrayFuture(Publisher<? extends ByteBuffer> publisher, int maxLength) {
        var f = new CompletableFuture<byte[]>();
        publisher.subscribe(new Subscriber<ByteBuffer>() {
            private final List<ByteBuffer> list = new ArrayList<>();
            private int length = 0;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer byteBuffer) {
                if (length < maxLength) {
                    list.add(byteBuffer);
                    length += byteBuffer.remaining();
                }
            }

            @Override
            public void onError(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                if (length == 0) {
                    f.complete(new byte[0]);
                    return;
                }
                var buf = new byte[length];
                var offset = 0;
                for (var byteBuffer : list) {
                    var remaining = byteBuffer.remaining();
                    byteBuffer.get(buf, offset, remaining);
                    offset += remaining;
                }
                f.complete(buf);
            }
        });
        return f;
    }

    public static CompletableFuture<ByteBuffer> toByteBufferFuture(Publisher<? extends ByteBuffer> publisher) {
        return toByteBufferFuture(publisher, Integer.MAX_VALUE);
    }

    public static CompletableFuture<ByteBuffer> toByteBufferFuture(Publisher<? extends ByteBuffer> publisher, int maxLength) {
        var f = new CompletableFuture<ByteBuffer>();
        publisher.subscribe(new Subscriber<ByteBuffer>() {
            private final List<ByteBuffer> list = new ArrayList<>();
            private int length = 0;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer byteBuffer) {
                if (length < maxLength) {
                    list.add(byteBuffer);
                    length += byteBuffer.remaining();
                }
            }

            @Override
            public void onError(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                if (length == 0) {
                    f.complete(ByteBuffer.allocate(0));
                    return;
                }
                var buf = ByteBuffer.allocate(length);
                for (var byteBuffer : list) {
                    buf.put(byteBuffer);
                }
                buf.flip();
                f.complete(buf);
            }
        });
        return f;
    }
}
