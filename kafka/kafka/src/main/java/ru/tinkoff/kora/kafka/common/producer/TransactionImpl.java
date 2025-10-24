package ru.tinkoff.kora.kafka.common.producer;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaPublisherTelemetry;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class TransactionImpl<P extends GeneratedPublisher> extends AtomicReference<TransactionImpl.TxState> implements TransactionalPublisher.Transaction<P> {
    private final P publisher;
    private final TransactionalPublisherImpl<P> pool;
    private final KafkaPublisherTelemetry.KafkaPublisherTransactionObservation observation;

    public enum TxState {
        INIT, COMMIT, ABORT
    }

    public TransactionImpl(P publisher, TransactionalPublisherImpl<P> pool) {
        this.publisher = publisher;
        this.observation = publisher.telemetry().observeTx();
        this.pool = pool;
        this.set(TxState.INIT);
    }


    @Override
    public P publisher() {
        return this.publisher;
    }

    @Override
    public Producer<byte[], byte[]> producer() {
        return this.publisher.producer();
    }

    @Override
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {
        if (this.get() == TxState.INIT) {
            this.publisher.producer().sendOffsetsToTransaction(offsets, groupMetadata);
            this.observation.observeOffsets(offsets, groupMetadata);
        } else {
            throw new IllegalStateException("Offsets cannot be sent to transaction from state " + this.get());
        }
    }

    @Override
    public void abort(@Nullable Throwable t) {
        if (this.compareAndSet(TxState.INIT, TxState.ABORT)) {
            try {
                this.publisher.producer().abortTransaction();
                if (t != null) {
                    observation.observeError(t);
                }
                observation.observeRollback(t);
            } catch (KafkaException e) {
                if (t != null) {
                    e.addSuppressed(t);
                }
                this.observation.observeError(e);
                this.pool.deleteFromPool(this.publisher);
                try {
                    this.publisher.producer().close();
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            } finally {
                this.observation.end();
            }
            this.pool.returnToPool(this.publisher);
        } else {
            throw new IllegalStateException("Transaction cannot be aborted from state " + this.get());
        }
    }

    @Override
    public void flush() {
        this.publisher.producer().flush();
    }

    @Override
    public void close() {
        if (this.compareAndSet(TxState.INIT, TxState.COMMIT)) {
            try {
                this.publisher.producer().commitTransaction();
                observation.observeCommit();
            } catch (KafkaException e) {
                this.observation.observeError(e);
                this.pool.deleteFromPool(this.publisher);
                try {
                    this.publisher.producer().close();
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            } finally {
                this.observation.end();
            }
            this.pool.returnToPool(this.publisher);
        }
    }
}
