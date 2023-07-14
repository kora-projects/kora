package ru.tinkoff.kora.kafka.common.consumer.containers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.kafka.common.KafkaUtils;
import ru.tinkoff.kora.kafka.common.KafkaUtils.NamedThreadFactory;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class KafkaAssignConsumerContainer<K, V> implements Lifecycle {

    private final static Logger logger = LoggerFactory.getLogger(KafkaAssignConsumerContainer.class);

    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private final AtomicLong backoffTimeout;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;
    private final int threads;
    private final KafkaConsumerConfig config;
    private final long refreshInterval;
    private final String consumerPrefix;
    private volatile ExecutorService executorService;

    private final BaseKafkaRecordsHandler<K, V> handler;
    private final Set<Consumer<K, V>> consumers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private final AtomicReference<List<TopicPartition>> partitions = new AtomicReference<>(new ArrayList<>());
    private final ArrayList<Long> offsets = new ArrayList<>();
    private final String topic;
    private final KafkaConsumerTelemetry<K, V> telemetry;

    public KafkaAssignConsumerContainer(
        KafkaConsumerConfig config,
        String topic,
        Deserializer<K> keyDeserializer,
        Deserializer<V> valueDeserializer,
        KafkaConsumerTelemetry<K, V> telemetry,
        BaseKafkaRecordsHandler<K, V> handler) {
        this.handler = Objects.requireNonNull(handler);
        this.backoffTimeout = new AtomicLong(config.backoffTimeout().toMillis());
        this.keyDeserializer = Objects.requireNonNull(keyDeserializer);
        this.valueDeserializer = Objects.requireNonNull(valueDeserializer);
        this.topic = Objects.requireNonNull(topic);
        this.threads = config.threads();
        this.config = config;
        this.refreshInterval = config.partitionRefreshInterval().toMillis();
        this.telemetry = Objects.requireNonNull(telemetry);
        this.consumerPrefix = KafkaUtils.getConsumerPrefix(this.config);
    }

    public void launchPollLoop(int number, long started) {
        while (isActive.get()) {
            final Consumer<K, V> consumer;
            try {
                var realConsumer = new KafkaConsumer<>(this.config.driverProperties(), new ByteArrayDeserializer(), new ByteArrayDeserializer());
                consumer = new ConsumerWrapper<>(realConsumer, keyDeserializer, valueDeserializer);
            } catch (KafkaException e) {
                logger.error("Kafka Consumer '{}' initialization failed", consumerPrefix, e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    logger.error("Kafka Consumer '{}' error interrupting thread", consumerPrefix, ie);
                }
                continue;
            }

            var allPartitions = this.partitions.get();
            var partitions = List.<TopicPartition>of();
            try (consumer) {
                consumers.add(consumer);
                logger.info("Kafka Consumer '{}' started in {}",
                    consumerPrefix, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());

                boolean isFirstPoll = true;
                while (isActive.get()) {
                    var changed = this.refreshPartitions(allPartitions);
                    if (changed) {
                        logger.info("Kafka Consumer '{}' refreshing and reassigning partitions...", consumerPrefix);

                        allPartitions = this.partitions.get();
                        partitions = new ArrayList<>(allPartitions.size() / threads + 1);
                        for (var i = number; i < allPartitions.size(); i++) {
                            if (i % this.config.threads() == number) {
                                partitions.add(allPartitions.get(i));
                            }
                        }

                        consumer.assign(partitions);
                        synchronized (this.offsets) {
                            this.offsets.ensureCapacity(partitions.size());
                            for (var partition : partitions) {
                                var offset = this.offsets.get(partition.partition());
                                if (offset == null) { // new partition
                                    if (config.offset().right() != null) {
                                        var resetTo = Objects.requireNonNull(config.offset().right());
                                        if (resetTo.equals("earliest")) {
                                            consumer.seekToBeginning(List.of(partition));
                                        } else {
                                            consumer.seekToEnd(List.of(partition));
                                        }
                                    } else if (config.offset().left() != null) {
                                        var resetToDuration = Objects.requireNonNull(config.offset().left());
                                        var resetTo = Instant.now().minus(resetToDuration).getEpochSecond();// todo millis?
                                        var resetToOffset = consumer.offsetsForTimes(Map.of(partition, resetTo)).get(partition).offset();
                                        consumer.seek(partition, resetToOffset);
                                    }
                                } else {
                                    consumer.seek(partition, offset + 1);
                                }
                            }
                        }
                    }

                    if (partitions.isEmpty()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignore) {}
                        continue;
                    }

                    try {
                        logger.trace("Kafka Consumer '{}' polling...", consumerPrefix);

                        var records = consumer.poll(config.pollTimeout());
                        if (isFirstPoll) {
                            logger.info("Kafka Consumer '{}' first poll in {}",
                                consumerPrefix, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                            isFirstPoll = false;
                        }

                        // log
                        if (!records.isEmpty() && logger.isTraceEnabled()) {
                            var logTopics = new HashSet<String>(records.partitions().size());
                            var logPartitions = new HashSet<Integer>(records.partitions().size());
                            for (TopicPartition partition : records.partitions()) {
                                logPartitions.add(partition.partition());
                                logTopics.add(partition.topic());
                            }

                            logger.trace("Kafka Consumer '{}' polled '{}' records from topics {} and partitions {}",
                                consumerPrefix, records.count(), logTopics, logPartitions);
                        } else if (!records.isEmpty() && logger.isDebugEnabled()) {
                            logger.debug("Kafka Consumer '{}' polled '{}' records", consumerPrefix, records.count());
                        } else {
                            logger.trace("Kafka Consumer '{}' polled '0' records", consumerPrefix);
                        }

                        handler.handle(records, consumer, false);
                        for (var partition : records.partitions()) {
                            var partitionRecords = records.records(partition);
                            var lastRecord = partitionRecords.get(partitionRecords.size() - 1);
                            synchronized (this.offsets) {
                                this.offsets.set(partition.partition(), lastRecord.offset());
                                this.refreshLag(consumer);
                            }
                        }

                        backoffTimeout.set(config.backoffTimeout().toMillis());
                    } catch (WakeupException ignore) {
                    } catch (Exception e) {
                        logger.error("Kafka Consumer '{}' got unhandled exception", consumerPrefix, e);
                        try {
                            Thread.sleep(backoffTimeout.get());
                        } catch (InterruptedException ie) {
                            logger.error("Kafka Consumer '{}' error interrupting thread", consumerPrefix, ie);
                        }
                        if (backoffTimeout.get() < 60000) backoffTimeout.set(backoffTimeout.get() * 2);
                        break;
                    } finally {
                        Context.clear();
                    }
                }
                Thread.interrupted();
            } finally {
                this.consumers.remove(consumer);
            }
        }
    }

    private void refreshLag(Consumer<K, V> consumer) {
        for (var entry : consumer.endOffsets(this.partitions.get()).entrySet()) {
            var p = entry.getKey();
            var latestOffset = entry.getValue();
            var currentOffset = this.offsets.get(p.partition());
            if (currentOffset != null) {
                var lag = latestOffset - currentOffset;
                this.telemetry.reportLag(p, lag);
            }
        }
    }

    private final AtomicLong lastUpdateTime = new AtomicLong(0);

    private boolean refreshPartitions(List<TopicPartition> partitions) {
        var updateTime = lastUpdateTime.get();
        var currentTime = System.currentTimeMillis();
        var oldPartitions = this.partitions.get();
        if (currentTime - updateTime <= refreshInterval) {
            return oldPartitions.size() != partitions.size();
        }

        if (lastUpdateTime.compareAndSet(updateTime, currentTime)) {
            // we have to create new consumer to ignore metadata cache
            try (var consumer = new KafkaConsumer<>(this.config.driverProperties(), new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
                var newPartitions = consumer.partitionsFor(this.topic);
                if (newPartitions.size() == partitions.size()) {
                    return false;
                }
                this.partitions.set(newPartitions.stream().map(p -> new TopicPartition(p.topic(), p.partition())).toList());
                synchronized (this.offsets) {
                    for (int i = this.offsets.size(); i < newPartitions.size(); i++) {
                        this.offsets.add(null);
                    }
                    if (oldPartitions.isEmpty()) {
                        var p = newPartitions.stream().skip(this.offsets.size()).map(i -> new TopicPartition(i.topic(), i.partition())).toList();
                        for (var entry : consumer.endOffsets(p).entrySet()) {
                            this.offsets.set(entry.getKey().partition(), entry.getValue());
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                lastUpdateTime.set(updateTime);
                throw e;
            }
        }
        return false;
    }

    @Override
    public Mono<Void> init() {
        return Mono.fromRunnable(() -> {
            var threads = this.threads;
            if (threads > 0) {
                if (this.topic != null) {
                    logger.debug("Kafka Consumer '{}' starting...", consumerPrefix);
                    final long started = System.nanoTime();

                    executorService = Executors.newFixedThreadPool(threads, new NamedThreadFactory(this.topic));
                    for (int i = 0; i < threads; i++) {
                        var number = i;
                        executorService.execute(() -> launchPollLoop(number, started));
                    }

                    logger.info("Kafka Consumer '{}' started in {}", consumerPrefix, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                }
            }
        });
    }

    @Override
    public Mono<Void> release() {
        return Mono.fromRunnable(() -> {
            if (isActive.compareAndSet(true, false)) {
                logger.debug("Kafka Consumer '{}' stopping...", consumerPrefix);
                final long started = System.nanoTime();

                for (var consumer : consumers) {
                    consumer.wakeup();
                }
                consumers.clear();
                if (executorService != null) {
                    executorService.shutdownNow();
                }

                logger.info("Kafka Consumer '{}' stopped in {}", consumerPrefix, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
            }
        });
    }
}
