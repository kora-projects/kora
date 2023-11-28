package ru.tinkoff.kora.http.server.undertow.request;

import io.undertow.io.Receiver;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.SameThreadExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class UndertowRequestHttpBodySubscription implements Subscription, Receiver.PartialBytesCallback, Receiver.ErrorCallback {

    static final AtomicLongFieldUpdater<UndertowRequestHttpBodySubscription> REQUESTED = AtomicLongFieldUpdater.newUpdater(UndertowRequestHttpBodySubscription.class, "demand");
    volatile long demand = 0;

    private final Subscriber<? super ByteBuffer> s;
    private final HttpServerExchange exchange;

    private final Queue<byte[]> prefetchedData;

    private boolean subscribed = false;

    private final AtomicBoolean done = new AtomicBoolean(false);

    private final Lock lock = new ReentrantLock();

    UndertowRequestHttpBodySubscription(Subscriber<? super ByteBuffer> s, HttpServerExchange exchange, Queue<byte[]> prefetchedData) {
        this.s = s;
        this.exchange = exchange;
        this.prefetchedData = prefetchedData;
        this.exchange.addExchangeCompleteListener((ex, next) -> {
            next.proceed();
            if (this.done.compareAndSet(false, true)) {
                try {
                    this.s.onError(new IllegalStateException("Response send before request body is fully read"));
                } catch (Exception e) {
                    //todo
                }
            }
        });
    }

    @Override
    public void request(long n) {
        assert n > 0;
        this.lock.lock();
        try {
            while (!prefetchedData.isEmpty()) {
                n--;
                try {
                    byte[] bytes = prefetchedData.poll();
                    s.onNext(ByteBuffer.wrap(bytes));
                } catch (Exception e) {
                    // todo
                }
                if (n == 0) {
                    return;
                }
            }

            var oldDemand = Operators.addCap(REQUESTED, this, n);
            if (oldDemand <= 0) {
                exchange.getConnection().getWorker().execute(() -> {
                    if (subscribed) {
                        exchange.getRequestReceiver().resume();
                    } else {
                        subscribed = true;
                        Connectors.executeRootHandler(ex -> {
                            ex.dispatch(SameThreadExecutor.INSTANCE, () -> ex.getRequestReceiver().receivePartialBytes(this, this));
                        }, exchange);
                    }
                });
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void cancel() {
        exchange.getConnection().getWorker().execute(() -> {
            exchange.getRequestReceiver().resume();
            exchange.getRequestReceiver().receiveFullBytes((exchange, message) -> {

            }, (exchange, e) -> {

            });
        });
    }

    @Override
    public void error(HttpServerExchange exchange, IOException e) {
        exchange.dispatch(SameThreadExecutor.INSTANCE, () -> {
            s.onError(e);
        });
    }

    @Override
    public void handle(HttpServerExchange exchange, byte[] message, boolean last) {
        this.lock.lock();
        var newDemand = REQUESTED.decrementAndGet(this);
        try {
            if (newDemand <= 0) {
                exchange.getRequestReceiver().pause();
                // pause should be dispatched to prevent exchange end
                exchange.dispatch(SameThreadExecutor.INSTANCE, () -> {
                    if (message.length > 0) {
                        try {
                            s.onNext(ByteBuffer.wrap(message));
                        } catch (Throwable e) {
                            // todo
                        }
                    }

                    if (last) {
                        this.done.set(true);
                        try {
                            s.onComplete();
                        } catch (Throwable e) {
                            // todo
                        }
                    }
                });

                return;
            }
        } finally {
            this.lock.unlock();
        }


        if (message.length > 0) {
            try {
                s.onNext(ByteBuffer.wrap(message));
            } catch (Throwable e) {
                // todo
            }
        }

        if (last) {
            this.done.set(true);
            // last chunk should be dispatched to prevent exchange end
            exchange.dispatch(SameThreadExecutor.INSTANCE, () -> {
                try {
                    s.onComplete();
                } catch (Throwable e) {
                    // todo
                }
            });
        }
    }
}
