package io.koraframework.http.server.netty.body;

import io.koraframework.http.server.netty.buffer.*;
import io.koraframework.http.server.netty.handler.*;
import io.netty.util.internal.shaded.org.jctools.queues.*;
import org.jspecify.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class KoraStreamingNettyBody implements Flow.Publisher<KoraReadBuffer>, AutoCloseable {
    private static final int MAX_BUFFERED_CHUNKS = 32;

    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final MpscChunkedArrayQueue<KoraReadBuffer> queue = new MpscChunkedArrayQueue<>(32, 4096);

    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    @Nullable
    private volatile Throwable error;
    @Nullable
    private volatile VolatileSubscription currentSubscription;
    @Nullable
    private volatile KoraStreamingInboundHandler inboundHandler;

    public KoraStreamingNettyBody() {
    }

    public void attachHandler(KoraStreamingInboundHandler handler) {
        inboundHandler = handler;
        if (cancelled.get()) {
            handler.onConsumerCancel();
        } else if (subscribed.get()) {
            handler.onConsumerStart();
            handler.onConsumerDemandChanged();
        }
    }

    public void add(KoraReadBuffer buffer) {
        if (cancelled.get()) {
            buffer.close();
            return;
        }
        queueSize.incrementAndGet();
        queue.offer(buffer);
        if (cancelled.get()) {
            drainQueueOnCancel();
            return;
        }
        drain();
    }

    public void complete() {
        completed.set(true);
        drain();
    }

    public void error(Throwable t) {
        error = t;
        drain();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super KoraReadBuffer> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        if (!subscribed.compareAndSet(false, true)) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            subscriber.onError(new IllegalStateException("ByteBody can only be subscribed to once."));
            return;
        }

        var sub = new VolatileSubscription(subscriber);
        currentSubscription = sub;
        var handler = inboundHandler;
        if (handler != null) {
            handler.onConsumerStart();
        }
        subscriber.onSubscribe(sub);
        sub.drain();
    }

    private void drain() {
        var sub = currentSubscription;
        if (sub != null) {
            sub.drain();
        }
    }

    public boolean isCompleted() {
        return completed.get();
    }

    @Override
    public void close() {
        if (cancelled.compareAndSet(false, true)) {
            drainQueueOnCancel();
            var handler = inboundHandler;
            if (handler != null) {
                handler.onConsumerCancel();
            }
        }
    }

    private void drainQueueOnCancel() {
        int removed = 0;
        KoraReadBuffer rb;
        while ((rb = queue.poll()) != null) {
            removed++;
            rb.close();
        }
        if (removed != 0) {
            queueSize.addAndGet(-removed);
        }
    }

    public boolean isWritable() {
        if (cancelled.get()) {
            return true;
        }
        var sub = currentSubscription;
        if (sub == null) {
            return queueSize.get() < MAX_BUFFERED_CHUNKS;
        }
        return queueSize.get() < MAX_BUFFERED_CHUNKS && sub.requested.get() > queueSize.get();
    }

    private final class VolatileSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super KoraReadBuffer> subscriber;
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicInteger wip = new AtomicInteger(0);
        private boolean terminated;

        VolatileSubscription(Flow.Subscriber<? super KoraReadBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                error = new IllegalArgumentException("Spec rule 3.9: n must be > 0");
                drain();
                return;
            }

            long current;
            long next;
            do {
                current = requested.get();
                next = current + n;
                if (next < 0) {
                    next = Long.MAX_VALUE;
                }
            } while (!requested.compareAndSet(current, next));

            if (!completed.get()) {
                var handler = inboundHandler;
                if (handler != null) {
                    handler.onConsumerDemandChanged();
                }
            }

            drain();
        }

        @Override
        public void cancel() {
            close();
        }

        void drain() {
            if (wip.getAndIncrement() != 0) {
                return;
            }

            int missed = 1;
            while (true) {
                if (!terminated) {
                    long req = requested.get();
                    long emitted = 0;
                    int polled = 0;

                    while (emitted != req) {
                        if (cancelled.get()) {
                            if (polled != 0) {
                                queueSize.addAndGet(-polled);
                                polled = 0;
                            }
                            drainQueueOnCancel();
                            terminated = true;
                            break;
                        }

                        var t = error;
                        if (t != null) {
                            terminated = true;
                            if (polled != 0) {
                                queueSize.addAndGet(-polled);
                                polled = 0;
                            }
                            drainQueueOnCancel();
                            subscriber.onError(t);
                            break;
                        }

                        var buffer = queue.poll();
                        boolean empty = buffer == null;

                        if (completed.get() && empty) {
                            terminated = true;
                            if (polled != 0) {
                                queueSize.addAndGet(-polled);
                                polled = 0;
                            }
                            subscriber.onComplete();
                            break;
                        }

                        if (empty) {
                            break;
                        }

                        polled++;

                        if (cancelled.get()) {
                            buffer.close();
                            if (polled != 0) {
                                queueSize.addAndGet(-polled);
                                polled = 0;
                            }
                            drainQueueOnCancel();
                            terminated = true;
                            break;
                        }

                        try {
                            subscriber.onNext(buffer);
                        } catch (Throwable e) {
                            buffer.close();
                            if (polled != 0) {
                                queueSize.addAndGet(-polled);
                                polled = 0;
                            }
                            terminated = true;
                            close();
                            subscriber.onError(e);
                            break;
                        }
                        emitted++;
                    }

                    if (polled != 0) {
                        queueSize.addAndGet(-polled);
                    }

                    if (!terminated) {
                        if (cancelled.get()) {
                            drainQueueOnCancel();
                            terminated = true;
                        } else if (error != null && queue.isEmpty()) {
                            terminated = true;
                            subscriber.onError(Objects.requireNonNull(error));
                        } else if (completed.get() && queue.isEmpty()) {
                            terminated = true;
                            subscriber.onComplete();
                        }
                    }

                    if (emitted != 0) {
                        if (req != Long.MAX_VALUE) {
                            requested.addAndGet(-emitted);
                        }
                        if (!terminated && !cancelled.get() && !completed.get()) {
                            var handler = inboundHandler;
                            if (handler != null) {
                                handler.onConsumerDemandChanged();
                            }
                        }
                    }
                }

                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
    }
}
