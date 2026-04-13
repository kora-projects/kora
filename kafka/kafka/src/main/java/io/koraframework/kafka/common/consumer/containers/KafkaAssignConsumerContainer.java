package io.koraframework.kafka.common.consumer.containers;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.kafka.common.KafkaUtils.NamedThreadFactory;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.consumer.Consumer;
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

    public void launchPollLoop(Consumer<K, V> consumer, String listenerName, int number, long started) {
        try (consumer) {
            var allPartitions = this.partitions.get();
            var partitions = List.<TopicPartition>of();
            logger.info("{} started in {}", listenerName, TimeUtils.tookForLogging(started));

            boolean isFirstPoll = true;
            boolean isFirstAssign = true;
            while (isActive.get()) {
                var changed = this.refreshPartitions(allPartitions);
                if (changed || isFirstAssign) {
                    if (changed) {
                        logger.info("{} refreshing and reassigning partitions...", listenerName);
                    }

                    allPartitions = this.partitions.get();
                    partitions = new ArrayList<>(allPartitions.size() / threads + 1);
                    for (var i = number; i < allPartitions.size(); i++) {
                        if (i % this.config.threads() == number) {
                            partitions.add(allPartitions.get(i));
                        }
                    }

                    consumer.assign(partitions);
                    logger.info("{} assigned {} partitions: {}", listenerName, partitions.size(), partitions);
                    isFirstAssign = false;
                    synchronized (this.offsets) {
                        this.offsets.ensureCapacity(partitions.size());
                        for (var partition : partitions) {
                            var offset = this.offsets.get(partition.partition());
                            if (offset == null) { // new partition
                                if (config.offset().right() != null) {
                                    var resetTo = Objects.requireNonNull(config.offset().right());
                                    logger.trace("{} seeking offset to '{}' for partition: {}", listenerName, resetTo, partition);
                                    switch (resetTo) {
                                        case earliest -> consumer.seekToBeginning(List.of(partition));
                                        case latest -> consumer.seekToEnd(List.of(partition));
                                    }
                                    logger.debug("{} succeeded seek offset to '{}' for partition: {}", listenerName, resetTo, partition);
                                } else if (config.offset().left() != null) {
                                    var resetToDuration = Objects.requireNonNull(config.offset().left());
                                    var resetTo = Instant.now().minus(resetToDuration).toEpochMilli();
                                    var resetToOffset = consumer.offsetsForTimes(Map.of(partition, resetTo)).get(partition).offset();
                                    logger.trace("{} seeking offset to '{}' to epochMillis '{}' for partition: {}", listenerName, resetToOffset, resetTo, partition);
                                    consumer.seek(partition, resetToOffset);
                                    logger.debug("{} succeeded seek offset to '{}' to epochMillis '{}' for partition: {}", listenerName, resetToOffset, resetTo, partition);
                                }
                            } else {
                                var nextOffset = offset + 1;
                                logger.trace("{} seeking offset to '{}' for partition: {}", listenerName, nextOffset, partition);
                                consumer.seek(partition, nextOffset);
                                logger.debug("{} succeeded seek offset to '{}' for partition: {}", listenerName, nextOffset, partition);
                            }
                        }
                    }
                }

                if (partitions.isEmpty()) {
                    try {
                        logger.debug("{} no partitions assigned, sleeping for 1000ms", listenerName);
                        Thread.sleep(1000);
                    } catch (InterruptedException ignore) {}
                    continue;
                }

                try {
                    logger.trace("{} polling...", listenerName);

                    var observation = this.telemetry.observePoll();
                    var records = consumer.poll(config.pollTimeout());
                    if (isFirstPoll) {
                        logger.info("{} first poll for '{}' records in {}",
                            listenerName, records.count(), TimeUtils.tookForLogging(started));
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

                        logger.trace("{} polled '{}' records from topics {} and partitions {}",
                            listenerName, records.count(), logTopics, logPartitions);
                    } else if (!records.isEmpty() && logger.isDebugEnabled()) {
                        logger.debug("{} polled '{}' records",
                            listenerName, records.count());
                    } else {
                        logger.trace("{} polled '0' records",
                            listenerName);
                    }

                    handler.handle(observation, records, consumer, false);
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
                    logger.error("{} got unhandled exception",
                        listenerName, e);
                    try {
                        logger.debug("{} backing off for {}ms...", listenerName, backoffTimeout.get());
                        Thread.sleep(backoffTimeout.get());
                    } catch (InterruptedException ie) {
                        logger.error("{} error interrupting thread",
                            listenerName, ie);
                    }
                    if (backoffTimeout.get() < 60000) {
                        backoffTimeout.set(backoffTimeout.get() * 2);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("{} poll loop got unhandled exception",
                listenerName, e);
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
            logger.error("KafkaListener '{}' failed to start in assign mode, due to: {}", listenerName, e.getMessage(), e);
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                logger.error("KafkaListener '{}' error interrupting thread", listenerName, ie);
            }
            return null;
        }
    }

    @Override
    public void init() {
        var threads = this.threads;
        if (threads > 0) {
            if (this.topic != null) {
                logger.debug("KafkaListener '{}' starting in assign mode...", listenerName);
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
                                initLatch.countDown();

                                var listenerName = (threads == 1)
                                    ? "KafkaListener '" + this.listenerName + "'"
                                    : "KafkaListener-" + number + " '" + this.listenerName + "'";
                                launchPollLoop(consumer, listenerName, number, started);
                            }
                        }
                    });
                }

                if(config.initializationFailTimeout() != null) {
                    try {
                        if(!initLatch.await(config.initializationFailTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
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
            logger.debug("KafkaListener '{}' stopping...", listenerName);
            var started = System.nanoTime();

            for (var consumer : consumers) {
                consumer.wakeup();
            }
            consumers.clear();
            if (executorService != null) {
                if (!shutdownExecutorService(executorService, config.shutdownWait())) {
                    logger.warn("KafkaListener '{}' failed completing graceful shutdown in {}", listenerName, config.shutdownWait());
                }
            }

            logger.info("KafkaListener '{}' stopped in {}", listenerName, TimeUtils.tookForLogging(started));
        }
    }

    private boolean shutdownExecutorService(ExecutorService executorService, Duration shutdownAwait) {
        boolean terminated = executorService.isTerminated();
        if (!terminated) {
            executorService.shutdown();
            try {
                logger.debug("KafkaListener '{}' awaiting graceful shutdown...", listenerName);
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
