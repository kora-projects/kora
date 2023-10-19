package ru.tinkoff.kora.common.util;


import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.flow.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

public class FlowUtils {
    public static <T> CompletionStage<T> toFuture(Publisher<T> publisher) {
        var future = new FutureSubscriber<T>();
        publisher.subscribe(future);
        return future;
    }

    public static <T> Publisher<T> empty(Context context) {
        return subscriber -> {
            var s = new EmptySubscription<T>(context, subscriber);
            subscriber.onSubscribe(s);
        };
    }

    public static <T> Publisher<T> one(Context context, T value) {
        return new OnePublisher<>(context, value);
    }

    public static <T> Publisher<T> fromCallable(Context context, Callable<T> value) {
        return new FromCallablePublisher<>(context, value);
    }

    public static <T> Publisher<T> error(Context context, Throwable error) {
        return subscriber -> {
            var s = new ErrorSubscription<>(subscriber, context, error);
            subscriber.onSubscribe(s);
        };
    }

    public static <T> Subscriber<T> drain() {
        return new DrainSubscriber<>();
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
                    list.add(byteBuffer.slice());
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
