package ru.tinkoff.kora.http.server.undertow.request;

import io.undertow.io.Receiver;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.SameThreadExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

final class UndertowRequestHttpBodySubscription0 implements Subscription, Receiver.PartialBytesCallback, Receiver.ErrorCallback {
    static final AtomicLongFieldUpdater<UndertowRequestHttpBodySubscription0> REQUESTED = AtomicLongFieldUpdater.newUpdater(UndertowRequestHttpBodySubscription0.class, "demand");
    volatile long demand;

    private final Subscriber<? super ByteBuffer> s;
    private final HttpServerExchange exchange;

    UndertowRequestHttpBodySubscription0(Subscriber<? super ByteBuffer> s, HttpServerExchange exchange) {
        this.s = s;
        this.exchange = exchange;
        exchange.getRequestReceiver().receivePartialBytes(this, this);
    }

    @Override
    public void request(long n) {
        assert n > 0;
        var oldDemand = demand;
        var newDemand = Operators.addCap(REQUESTED, this, n);
        if (oldDemand <= 0 && newDemand > 0) {
            exchange.getConnection().getWorker().execute(() -> {
                exchange.getRequestReceiver().resume();
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
        exchange.dispatch(SameThreadExecutor.INSTANCE, exchange1 -> {
            var newDemand = REQUESTED.decrementAndGet(this);
            assert newDemand >= 0;
            if (newDemand > 0) {
                exchange1.getRequestReceiver().resume();
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
