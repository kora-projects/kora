package ru.tinkoff.kora.http.server.undertow.request;

import io.undertow.io.Receiver;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.SameThreadExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

final class UndertowRequestHttpBodySubscription2 implements Subscription, Receiver.PartialBytesCallback, Receiver.ErrorCallback {
    static final AtomicLongFieldUpdater<UndertowRequestHttpBodySubscription2> REQUESTED = AtomicLongFieldUpdater.newUpdater(UndertowRequestHttpBodySubscription2.class, "demand");
    volatile long demand = 0;

    private final Subscriber<? super ByteBuffer> s;
    private final HttpServerExchange exchange;
    private volatile byte[] firstData;
    private volatile byte[] secondData;
    private boolean subscribed = false;

    UndertowRequestHttpBodySubscription2(Subscriber<? super ByteBuffer> s, HttpServerExchange exchange, byte[] firstData, byte[] secondData) {
        this.s = s;
        this.exchange = exchange;
        this.firstData = Objects.requireNonNull(firstData);
        this.secondData = Objects.requireNonNull(secondData);
    }

    @Override
    public void request(long n) {
        assert n > 0;
        synchronized (this) {
            var firstData = this.firstData;
            if (firstData != null) {
                this.firstData = null;
                n--;
                try {
                    s.onNext(ByteBuffer.wrap(firstData));
                } catch (Exception e) {
                    // todo
                }
                if (n == 0) {
                    return;
                }
            }
            var secondData = this.secondData;
            if (secondData != null) {
                this.secondData = null;
                n--;
                try {
                    s.onNext(ByteBuffer.wrap(secondData));
                } catch (Exception e) {
                    // todo
                }
                if (n == 0) {
                    return;
                }
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
        try {
            exchange.getConnection().close();
        } catch (IOException ex) {
            e.addSuppressed(ex);
        }
        s.onError(e);
    }

    @Override
    public void handle(HttpServerExchange exchange, byte[] message, boolean last) {
        exchange.getRequestReceiver().pause();
        exchange.dispatch(SameThreadExecutor.INSTANCE, () -> {
            var newDemand = REQUESTED.decrementAndGet(this);
            assert newDemand >= 0 : "newDemand(" + newDemand + ") >= 0 failed";
            if (newDemand > 0) {
                exchange.getRequestReceiver().resume();
            }
            if (message.length > 0) {
                try {
                    s.onNext(ByteBuffer.wrap(message));
                } catch (Throwable e) {
                    // todo
                }
            }
            if (last) {
                try {
                    s.onComplete();
                } catch (Throwable e) {
                    // todo
                }
            }
        });
    }
}
