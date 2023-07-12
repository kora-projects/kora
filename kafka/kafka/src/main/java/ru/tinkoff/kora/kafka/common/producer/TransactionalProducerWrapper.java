package ru.tinkoff.kora.kafka.common.producer;

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.*;
import org.apache.kafka.common.errors.ProducerFencedException;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class TransactionalProducerWrapper<K, V> implements Producer<K, V> {
    private enum TxState {
        STATE_NO_TX, STATE_IN_TX
    }

    private final TransactionalProducerImpl<K, V> pool;
    private final Producer<K, V> delegate;
    private final KafkaProducerTelemetry telemetry;
    private final TransactionalProducerImpl.ProducerWithTelemetry<K, V> producerWithTelemetry;
    private volatile TxState state = TxState.STATE_NO_TX;
    private volatile KafkaProducerTelemetry.KafkaProducerTransactionTelemetryContext txTelemetry;

    public TransactionalProducerWrapper(TransactionalProducerImpl<K, V> pool, TransactionalProducerImpl.ProducerWithTelemetry<K, V> producerWithTelemetry) {
        this.pool = pool;
        this.delegate = producerWithTelemetry.producer();
        this.telemetry = producerWithTelemetry.telemetry();
        this.producerWithTelemetry = producerWithTelemetry;
    }

    @Override
    public void initTransactions() {
        // was called on delegate when creating
    }

    @Override
    public void beginTransaction() throws ProducerFencedException {
        delegate.beginTransaction();
        state = TxState.STATE_IN_TX;
        this.txTelemetry = this.telemetry.tx();
    }

    @Override
    @Deprecated
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, String consumerGroupId) throws ProducerFencedException {
        delegate.sendOffsetsToTransaction(offsets, consumerGroupId);
        state = TxState.STATE_IN_TX;
    }

    @Override
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) throws ProducerFencedException {
        this.txTelemetry.sendOffsetsToTransaction(offsets, groupMetadata);
        delegate.sendOffsetsToTransaction(offsets, groupMetadata);
        state = TxState.STATE_IN_TX;
    }

    @Override
    public void commitTransaction() throws ProducerFencedException {
        delegate.commitTransaction();
        state = TxState.STATE_NO_TX;
        txTelemetry.commit();
    }

    @Override
    public void abortTransaction() throws ProducerFencedException {
        delegate.abortTransaction();
        state = TxState.STATE_NO_TX;
        txTelemetry.rollback(null);
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
        return delegate.send(record);
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
        return delegate.send(record, callback);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public List<PartitionInfo> partitionsFor(String topic) {
        return delegate.partitionsFor(topic);
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return delegate.metrics();
    }

    @Override
    public void close() {
        if (state == TxState.STATE_IN_TX) {
            try {
                this.commitTransaction();
            } catch (KafkaException e) {
                this.pool.deleteFromPool(this.producerWithTelemetry);
                try {
                    this.delegate.close();
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
        }
        this.pool.returnToPool(this.producerWithTelemetry);
    }

    @Override
    public void close(Duration timeout) {
        if (state == TxState.STATE_IN_TX) {
            try {
                this.commitTransaction();
            } catch (KafkaException e) {
                this.pool.deleteFromPool(this.producerWithTelemetry);
                try {
                    this.delegate.close(timeout);
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
        }
        this.pool.returnToPool(this.producerWithTelemetry);
    }
}
