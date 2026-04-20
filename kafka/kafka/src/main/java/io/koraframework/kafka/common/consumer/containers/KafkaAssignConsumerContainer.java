package io.koraframework.kafka.common.consumer.containers;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.kafka.common.KafkaUtils.NamedThreadFactory;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerPollObservation;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class KafkaAssignConsumerContainer<K, V> implements Lifecycle {

    private final Logger logger;

    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private final AtomicLong backoffTimeout;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;
    private final int threads;
    private final KafkaListenerConfig config;
    private final long refreshInterval;
    private final String listenerName;
    private final String listenerImpl;
    private volatile ExecutorService executorService;

    private final BaseKafkaRecordsHandler<K, V> handler;
    private final Set<Consumer<K, V>> consumers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private final AtomicReference<List<TopicPartition>> partitions = new AtomicReference<>(new ArrayList<>());
    private final ArrayList<Long> offsets = new ArrayList<>();
    private final String topic;
    private final KafkaConsumerTelemetry telemetry;

    public KafkaAssignConsumerContainer(String listenerName,
                                        String listenerImpl,
                                        KafkaListenerConfig config,
                                        String topic,
                                        Deserializer<K> keyDeserializer,
                                        Deserializer<V> valueDeserializer,
                                        KafkaConsumerTelemetry telemetry,
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
        this.listenerName = Objects.requireNonNull(listenerName);
        this.listenerImpl = Objects.requireNonNull(listenerImpl);
        var logger = LoggerFactory.getLogger(listenerImpl);
        this.logger = this.config.telemetry().logging().enabled() && logger.isErrorEnabled()
            ? logger
            : NOPLogger.NOP_LOGGER;
    }

    public void launchPollLoop(Consumer<K, V> consumer, String listenerLogName, int number, long started, Runnable initializeConfirmer) {
        try (consumer) {
            var allPartitions = this.partitions.get();
            var partitions = List.<TopicPartition>of();
            logger.atInfo()
                .addKeyValue("listenerName", this.listenerName)
                .log("{} started in {}", listenerLogName, TimeUtils.tookForLogging(started));

            boolean isFirstPoll = true;
            boolean isFirstAssign = true;
            boolean isInitializeOnFirstAssignEmpty = false;

            while (isActive.get()) {
                var changed = this.refreshPartitions(allPartitions);
                if (changed || isFirstAssign) {
                    if (changed) {
                        logger.atInfo()
                            .addKeyValue("listenerName", this.listenerName)
                            .log("{} refreshing and reassigning partitions...", listenerLogName);
                    }

                    allPartitions = this.partitions.get();
                    partitions = new ArrayList<>(allPartitions.size() / threads + 1);
                    for (var i = number; i < allPartitions.size(); i++) {
                        if (i % this.config.threads() == number) {
                            partitions.add(allPartitions.get(i));
                        }
                    }

                    consumer.assign(partitions);
                    logger.atInfo()
                        .addKeyValue("listenerName", this.listenerName)
                        .log("{} assigned {} partitions: {}", listenerLogName, partitions.size(), partitions);
                    synchronized (this.offsets) {
                        this.offsets.ensureCapacity(partitions.size());
                        for (var partition : partitions) {
                            var offset = this.offsets.get(partition.partition());
                            if (offset == null) { // new partition
                                if (config.offset().right() != null) {
                                    var resetTo = Objects.requireNonNull(config.offset().right());
                                    logger.atTrace()
                                        .addKeyValue("listenerName", this.listenerName)
                                        .log("{} seeking offset to '{}' for partition: {}", listenerLogName, resetTo, partition);
                                    switch (resetTo) {
                                        case earliest -> consumer.seekToBeginning(List.of(partition));
                                        case latest -> consumer.seekToEnd(List.of(partition));
                                    }
                                    logger.atDebug()
                                        .addKeyValue("listenerName", this.listenerName)
                                        .log("{} succeeded seek offset to '{}' for partition: {}", listenerLogName, resetTo, partition);
                                } else if (config.offset().left() != null) {
                                    var resetToDuration = Objects.requireNonNull(config.offset().left());
                                    var resetTo = Instant.now().minus(resetToDuration).toEpochMilli();
                                    var resetToOffset = consumer.offsetsForTimes(Map.of(partition, resetTo)).get(partition).offset();
                                    logger.atTrace()
                                        .addKeyValue("listenerName", this.listenerName)
                                        .log("{} seeking offset to '{}' to epochMillis '{}' for partition: {}", listenerLogName, resetToOffset, resetTo, partition);
                                    consumer.seek(partition, resetToOffset);
                                    logger.atDebug()
                                        .addKeyValue("listenerName", this.listenerName)
                                        .log("{} succeeded seek offset to '{}' to epochMillis '{}' for partition: {}", listenerLogName, resetToOffset, resetTo, partition);
                                }
                            } else {
                                var nextOffset = offset + 1;
                                logger.atTrace()
                                    .addKeyValue("listenerName", this.listenerName)
                                    .log("{} seeking offset to '{}' for partition: {}", listenerLogName, nextOffset, partition);
                                consumer.seek(partition, nextOffset);
                                logger.atDebug()
                                    .addKeyValue("listenerName", this.listenerName)
                                    .log("{} succeeded seek offset to '{}' for partition: {}", listenerLogName, nextOffset, partition);
                            }
                        }
                    }

                    if (isFirstAssign) {
                        if (!partitions.isEmpty()) {
                            isFirstAssign = false;
                        } else if (!isInitializeOnFirstAssignEmpty) {
                            initializeConfirmer.run();
                            isInitializeOnFirstAssignEmpty = true;
                        }
                    }
                }

                if (partitions.isEmpty()) {
                    try {
                        logger.atDebug()
                            .addKeyValue("listenerName", this.listenerName)
                            .log("{} no partitions assigned, sleeping for 1000ms", listenerLogName);
                        Thread.sleep(1000);
                    } catch (InterruptedException ignore) {}
                    continue;
                }

                KafkaConsumerPollObservation observation = null;
                try {
                    observation = this.telemetry.observePoll();
                    final ConsumerRecords<K, V> records;
                    if (isFirstPoll) {
                        records = consumer.poll(Duration.ofMillis(10));
                        logger.atInfo()
                            .addKeyValue("listenerName", this.listenerName)
                            .log("{} first poll for '{}' records in {}",
                                listenerLogName, records.count(), TimeUtils.tookForLogging(started));

                        isFirstPoll = false;
                        if (!isInitializeOnFirstAssignEmpty) {
                            initializeConfirmer.run();
                        }
                    } else {
                        records = consumer.poll(config.pollTimeout());
                    }

                    handler.handle(observation, records, consumer, false);
                    for (var partition : records.partitions()) {
                        var partitionRecords = records.records(partition);
                        var lastRecord = partitionRecords.getLast();
                        synchronized (this.offsets) {
                            this.offsets.set(partition.partition(), lastRecord.offset());
                            this.refreshLag(consumer);
                        }
                    }

                    backoffTimeout.set(config.backoffTimeout().toMillis());
                } catch (WakeupException ignore) {
                } catch (Exception e) {
                    if (observation != null) {
                        observation.observeError(e);
                        observation.end();
                    }

                    try {
                        logger.atDebug()
                            .addKeyValue("listenerName", this.listenerName)
                            .log("{} backing off for {}ms...", listenerLogName, backoffTimeout.get());
                        Thread.sleep(backoffTimeout.get());
                    } catch (InterruptedException ie) {
                        logger.atError()
                            .addKeyValue("listenerName", this.listenerName)
                            .log("{} error interrupting thread", listenerLogName, ie);
                    }
                    if (backoffTimeout.get() < 60000) {
                        backoffTimeout.set(backoffTimeout.get() * 2);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.atError()
                .addKeyValue("listenerName", this.listenerName)
                .log("{} poll loop got unhandled exception", listenerLogName, e);
        } finally {
            consumers.remove(consumer);
        }
    }

    private void refreshLag(Consumer<K, V> consumer) {
        for (var entry : consumer.endOffsets(this.partitions.get()).entrySet()) {
            var p = entry.getKey();
            var latestOffset = entry.getValue();
            var currentOffset = this.offsets.get(p.partition());
            if (currentOffset != null) {
                // add -1 cause
                // In the default read_uncommitted isolation level endOffsets() returns, the end offset is the high watermark
                // (that is, the offset of the last successfully replicated message plus one)
                var lag = latestOffset - currentOffset - 1;
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

    @Nullable
    private Consumer<K, V> initializeConsumer() {
        try {
            var realConsumer = new KafkaConsumer<>(this.config.driverProperties(), new ByteArrayDeserializer(), new ByteArrayDeserializer());
            var driverMetrics = (KafkaClientMetrics) null;
            if (this.config.telemetry().metrics().driverMetrics()) {
                var metrics = new KafkaClientMetrics(realConsumer);
                metrics.bindTo(this.telemetry.meterRegistry());
                driverMetrics = metrics;
            }
            return new ConsumerWrapper<>(realConsumer, driverMetrics, keyDeserializer, valueDeserializer);
        } catch (Exception e) {
            logger.atError()
                .addKeyValue("listenerName", this.listenerName)
                .log("KafkaListener failed to start in assign mode, due to: {}", e.getMessage(), e);
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                logger.atError()
                    .addKeyValue("listenerName", this.listenerName)
                    .log("KafkaListener error interrupting thread", ie);
            }
            return null;
        }
    }

    @Override
    public void init() {
        var threads = this.threads;
        if (threads > 0) {
            if (this.topic != null) {
                logger.atDebug()
                    .addKeyValue("listenerName", this.listenerName)
                    .log("KafkaListener starting in assign mode...", listenerName);
                final long started = TimeUtils.started();

                executorService = Executors.newFixedThreadPool(threads, new NamedThreadFactory(listenerName));
                CountDownLatch initLatch = new CountDownLatch(config.threads());

                for (int i = 0; i < threads; i++) {
                    var number = i;
                    executorService.execute(() -> {
                        // infinite try to init in cycle, will go into infinite pool loop if init success
                        while (isActive.get()) {
                            var consumer = initializeConsumer();
                            if (consumer != null) {
                                var listenerName = (threads == 1)
                                    ? "KafkaListener '" + this.listenerName + "'"
                                    : "KafkaListener-" + number + " '" + this.listenerName + "'";
                                launchPollLoop(consumer, listenerName, number, started, initLatch::countDown);
                            }
                        }
                    });
                }

                if (config.initializationFailTimeout() != null) {
                    try {
                        if (!initLatch.await(config.initializationFailTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                            throw new RuntimeException("KafkaListener '{}' failed to start, due to timeout in {}ms".formatted(
                                listenerName, TimeUtils.durationForLogging(config.initializationFailTimeout())));
                        }
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }
        }
    }

    @Override
    public void release() {
        if (isActive.compareAndSet(true, false)) {
            logger.atDebug()
                .addKeyValue("listenerName", this.listenerName)
                .log("KafkaListener stopping...");
            var started = System.nanoTime();

            for (var consumer : consumers) {
                consumer.wakeup();
            }
            consumers.clear();
            if (executorService != null) {
                if (!shutdownExecutorService(executorService, config.shutdownWait())) {
                    logger.atWarn()
                        .addKeyValue("listenerName", this.listenerName)
                        .log("KafkaListener failed completing graceful shutdown in {}", config.shutdownWait());
                }
            }

            logger.atInfo()
                .addKeyValue("listenerName", this.listenerName)
                .log("KafkaListener stopped in {}", TimeUtils.tookForLogging(started));
        }
    }

    private boolean shutdownExecutorService(ExecutorService executorService, Duration shutdownAwait) {
        boolean terminated = executorService.isTerminated();
        if (!terminated) {
            executorService.shutdown();
            try {
                logger.atDebug()
                    .addKeyValue("listenerName", this.listenerName)
                    .log("KafkaListener awaiting graceful shutdown...");
                terminated = executorService.awaitTermination(shutdownAwait.toMillis(), TimeUnit.MILLISECONDS);
                if (!terminated) {
                    executorService.shutdownNow();
                }
                return terminated;
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                return false;
            }
        } else {
            return true;
        }
    }
}
