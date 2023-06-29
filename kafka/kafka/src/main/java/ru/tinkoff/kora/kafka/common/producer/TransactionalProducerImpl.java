package ru.tinkoff.kora.kafka.common.producer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.Serializer;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetry;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetryFactory;

import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TransactionalProducerImpl<K, V> implements TransactionalProducer<K, V>, Lifecycle {
    private final BlockingDeque<ProducerWithTelemetry<K, V>> pool = new LinkedBlockingDeque<>();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final KafkaProducerTelemetryFactory producerTelemetryFactory;
    private final AtomicInteger size = new AtomicInteger(0);
    private final PublisherConfig config;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private final PublisherConfig.TransactionConfig transactionConfig;

    public TransactionalProducerImpl(KafkaProducerTelemetryFactory factory, PublisherConfig config, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.producerTelemetryFactory = factory;
        this.config = config;
        this.transactionConfig = Objects.requireNonNull(config.transaction());
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    record ProducerWithTelemetry<K, V>(Producer<K, V> producer, KafkaProducerTelemetry telemetry) {}

    @Override
    public Producer<K, V> begin() {
        if (this.isClosed.get()) {
            throw new IllegalStateException();
        }
        var pooled = this.pool.pollFirst();
        if (pooled != null) {
            var tx = new TransactionalProducerWrapper<>(this, pooled);
            try {
                tx.beginTransaction();
            } catch (Exception e) {
                this.size.decrementAndGet();
                pooled.producer.close();
                throw e;
            }
            return tx;
        }
        if (this.size.incrementAndGet() > this.transactionConfig.maxPoolSize()) {
            this.size.decrementAndGet();
            try {
                var waited = this.pool.pollFirst(this.transactionConfig.maxWaitTime().toMillis(), TimeUnit.MILLISECONDS);
                if (waited != null) {
                    var tx = new TransactionalProducerWrapper<>(this, waited);
                    try {
                        tx.beginTransaction();
                    } catch (Exception e) {
                        this.size.decrementAndGet();
                        waited.producer.close();
                        throw e;
                    }
                    return tx;
                }
                throw new TimeoutException("Pooled producer was not available after " + this.transactionConfig.maxWaitTime());
            } catch (InterruptedException e) {
                throw new KafkaException(e);
            }
        }
        var p = this.createNewProducer();
        var tx = new TransactionalProducerWrapper<>(this, p);
        try {
            tx.beginTransaction();
        } catch (Exception e) {
            this.size.decrementAndGet();
            throw e;
        }
        return tx;
    }

    private ProducerWithTelemetry<K, V> createNewProducer() {
        var properties = this.config.driverProperties();
        var realProperties = new Properties();
        realProperties.putAll(properties);
        realProperties.put(org.apache.kafka.clients.producer.ProducerConfig.TRANSACTIONAL_ID_CONFIG, this.transactionConfig.idPrefix() + "-" + UUID.randomUUID());
        var p = new KafkaProducer<>(realProperties, this.keySerializer, this.valueSerializer);
        try {
            p.initTransactions();
        } catch (Exception e) {
            p.close();
            throw e;
        }
        var t = this.producerTelemetryFactory.get(p, realProperties);
        return new ProducerWithTelemetry<>(p, t);
    }

    public void returnToPool(ProducerWithTelemetry<K, V> wrapper) {
        if (this.isClosed.get()) {
            wrapper.producer().close();
        } else {
            this.pool.addFirst(wrapper);
        }
    }

    public void deleteFromPool(ProducerWithTelemetry<K, V> wrapper) {
        this.size.decrementAndGet();
        wrapper.telemetry().close();
    }

    @Override
    public Mono<?> init() {
        return Mono.empty();
    }

    @Override
    public Mono<?> release() {
        return ReactorUtils.ioMono(() -> {
            if (this.isClosed.compareAndSet(false, true)) {
                for (var p : this.pool) {
                    p.producer().close();
                    p.telemetry().close();
                }
            }
        });
    }
}
