package ru.tinkoff.kora.kafka.common.producer;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import ru.tinkoff.kora.application.graph.Lifecycle;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class TransactionalPublisherImpl<P extends GeneratedPublisher> implements TransactionalPublisher<P>, Lifecycle {
    private final BlockingDeque<P> pool = new LinkedBlockingDeque<>();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicInteger size = new AtomicInteger(0);
    private final Supplier<? extends P> factory;

    private final KafkaPublisherConfig.TransactionConfig transactionConfig;

    public TransactionalPublisherImpl(KafkaPublisherConfig.TransactionConfig config, Supplier<? extends P> factory) {
        this.transactionConfig = Objects.requireNonNull(config);
        this.factory = factory;
    }

    @Nonnull
    @Override
    public final Transaction<P> begin() {
        if (this.isClosed.get()) {
            throw new IllegalStateException("Pool has already closed!");
        }

        var pooledWrapper = this.pool.pollFirst();
        if (pooledWrapper != null) {
            var producer = pooledWrapper.producer();
            try {
                producer.beginTransaction();
            } catch (Throwable e) {
                this.size.decrementAndGet();
                producer.close();
                throw e;
            }
            return new TransactionImpl<>(pooledWrapper, this);
        }

        if (this.size.incrementAndGet() > this.transactionConfig.maxPoolSize()) {
            this.size.decrementAndGet();
            try {
                var waitedWrapper = this.pool.pollFirst(this.transactionConfig.maxWaitTime().toMillis(), TimeUnit.MILLISECONDS);
                if (waitedWrapper != null) {
                    var producer = waitedWrapper.producer();
                    try {
                        producer.beginTransaction();
                    } catch (Throwable e) {
                        this.size.decrementAndGet();
                        producer.close();
                        throw e;
                    }
                    return new TransactionImpl<>(waitedWrapper, this);
                }
                throw new TimeoutException("Pooled producer was not available after " + this.transactionConfig.maxWaitTime());
            } catch (InterruptedException e) {
                throw new KafkaException(e);
            }
        }

        var p = this.createNewProducer();
        try {
            p.producer().beginTransaction();
        } catch (Throwable e) {
            this.size.decrementAndGet();
            throw e;
        }
        return new TransactionImpl<>(p, this);
    }

    private P createNewProducer() {
        var p = this.factory.get();
        try {
            p.init();
        } catch (Throwable e) {
            try {
                p.release();
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            if (e instanceof RuntimeException re) throw re;
            if (e instanceof Error re) throw re;
            throw new RuntimeException(e);
        }
        p.producer().initTransactions();
        return p;
    }

    public final void returnToPool(P p) {
        if (this.isClosed.get()) {
            p.producer().close();
        } else {
            this.pool.addFirst(p);
        }
    }

    public final void deleteFromPool(P p) {
        this.size.decrementAndGet();
        p.producer().close();
    }

    @Override
    public void init() {
    }

    @Override
    public void release() throws Exception {
        if (this.isClosed.compareAndSet(false, true)) {
            for (var p : this.pool) {
                p.release();
            }
        }
    }
}
