package io.koraframework.kafka.common.producer;

import io.koraframework.application.graph.Lifecycle;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;

import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class TransactionalPublisherImpl<P extends GeneratedPublisher> implements TransactionalPublisher<P>, Lifecycle {
    private final BlockingDeque<P> pool = new LinkedBlockingDeque<>();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final Supplier<? extends P> factory;
    // a permit is held by every producer that is in a transaction; idle pooled producers hold none
    private final Semaphore slots;

    private final KafkaPublisherConfig.TransactionConfig transactionConfig;

    public TransactionalPublisherImpl(KafkaPublisherConfig.TransactionConfig config, Supplier<? extends P> factory) {
        this.transactionConfig = Objects.requireNonNull(config);
        this.factory = factory;
        this.slots = new Semaphore(config.maxPoolSize());
    }

    @Override
    public final Transaction<P> begin() {
        if (this.isClosed.get()) {
            throw new IllegalStateException("Kafka transactional publisher pool is already closed; create transactions only while application component is active");
        }

        try {
            if (!this.slots.tryAcquire(this.transactionConfig.maxWaitTime().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Pooled producer was not available after " + this.transactionConfig.maxWaitTime());
            }
        } catch (InterruptedException e) {
            throw new KafkaException(e);
        }

        var p = this.pool.pollFirst();
        if (p == null) {
            try {
                p = this.createNewProducer();
            } catch (Throwable e) {
                this.slots.release();
                throw e;
            }
        }
        try {
            p.producer().beginTransaction();
        } catch (Throwable e) {
            try {
                this.deleteFromPool(p);
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
        return new TransactionImpl<>(p, this);
    }

    private P createNewProducer() {
        var p = this.factory.get();
        try {
            p.init();
            p.producer().initTransactions();
        } catch (Throwable e) {
            try {
                p.release();
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            if (e instanceof RuntimeException re) throw re;
            if (e instanceof Error re) throw re;
            throw new IllegalStateException("Kafka transactional publisher failed to create a producer for transaction pool; check producer startup cause", e);
        }
        return p;
    }

    public final void returnToPool(P p) {
        if (this.isClosed.get()) {
            try {
                this.deleteFromPool(p);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new KafkaException(e);
            }
        } else {
            this.pool.addFirst(p);
            this.slots.release();
        }
    }

    public final void deleteFromPool(P p) throws Exception {
        try {
            p.release();
        } finally {
            this.slots.release();
        }
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
