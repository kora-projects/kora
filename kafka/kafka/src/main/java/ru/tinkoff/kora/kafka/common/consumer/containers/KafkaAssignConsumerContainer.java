package ru.tinkoff.kora.kafka.common.consumer.containers;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.LogTruncationException;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.kafka.common.KafkaUtils;
import ru.tinkoff.kora.kafka.common.KafkaUtils.NamedThreadFactory;
import ru.tinkoff.kora.kafka.common.consumer.KafkaListenerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class KafkaAssignConsumerContainer<K, V> implements Lifecycle {

    private final static Logger logger = LoggerFactory.getLogger(KafkaAssignConsumerContainer.class);
    private static final Duration POSITION_CHANGE_TIMEOUT = Duration.ofSeconds(1);

    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private final Map<Integer, WorkerState> workers = new ConcurrentHashMap<>();
    private final Object backoffMonitor = new Object();
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;
    private final int threads;
    private final KafkaListenerConfig config;
    private final long refreshInterval;
    private final String consumerPrefix;
    private volatile ExecutorService executorService;

    private final BaseKafkaRecordsHandler<K, V> handler;
    private final Set<Consumer<K, V>> consumers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private final AtomicReference<List<TopicPartition>> partitions = new AtomicReference<>(new ArrayList<>());
    private final ArrayList<Long> offsets = new ArrayList<>();
    private final String topic;
    private final KafkaConsumerTelemetry<K, V> telemetry;

    public KafkaAssignConsumerContainer(KafkaListenerConfig config,
                                        String topic,
                                        Deserializer<K> keyDeserializer,
                                        Deserializer<V> valueDeserializer,
                                        KafkaConsumerTelemetry<K, V> telemetry,
                                        BaseKafkaRecordsHandler<K, V> handler) {
        this(KafkaUtils.getConsumerPrefix(config), config, topic, keyDeserializer, valueDeserializer, telemetry, handler);
    }

    public KafkaAssignConsumerContainer(String consumerName,
                                        KafkaListenerConfig config,
                                        String topic,
                                        Deserializer<K> keyDeserializer,
                                        Deserializer<V> valueDeserializer,
                                        KafkaConsumerTelemetry<K, V> telemetry,
                                        BaseKafkaRecordsHandler<K, V> handler) {
        this.handler = Objects.requireNonNull(handler);
        this.keyDeserializer = Objects.requireNonNull(keyDeserializer);
        this.valueDeserializer = Objects.requireNonNull(valueDeserializer);
        this.topic = Objects.requireNonNull(topic);
        this.threads = config.threads();
        this.config = config;
        this.refreshInterval = config.partitionRefreshInterval().toMillis();
        this.telemetry = Objects.requireNonNull(telemetry);
        if (consumerName == null || consumerName.isBlank()) {
            this.consumerPrefix = KafkaUtils.getConsumerPrefix(config);
        } else {
            this.consumerPrefix = consumerName;
        }
    }

    public void launchPollLoop(Consumer<K, V> consumer, int number, long started) {
        var retry = false;
        var worker = workers.computeIfAbsent(number, ignored -> new WorkerState(config.backoffTimeout().toMillis()));
        try (consumer; var t = telemetry.get(consumer)) {
            consumers.add(consumer);
            var allPartitions = this.partitions.get();
            var partitions = List.<TopicPartition>of();
            logger.info("Kafka Consumer '{}' started in {}", consumerPrefix, TimeUtils.tookForLogging(started));

            boolean isFirstPoll = true;
            boolean isFirstAssign = true;
            while (this.isRunning()) {
                try {
                    var changed = this.refreshPartitions(allPartitions);
                    var retryDue = false;
                    var now = System.nanoTime();
                    for (var entry : worker.retries.values()) {
                        if (!entry.probing && !entry.requiresOperator && now - entry.nextRetryAt >= 0) {
                            entry.probing = true;
                            retryDue = true;
                        }
                    }
                    if (changed || isFirstAssign || retryDue) {
                        logger.info("Kafka Consumer '{}' refreshing and reassigning partitions...", consumerPrefix);

                        allPartitions = this.partitions.get();
                        partitions = new ArrayList<>(allPartitions.size() / threads + 1);
                        for (var partition : allPartitions) {
                            if (partition.partition() % this.threads == number
                                && (!worker.retries.containsKey(partition) || worker.retries.get(partition).probing)) {
                                partitions.add(partition);
                            }
                        }

                        consumer.assign(partitions);
                        isFirstAssign = false;
                        var newPartitions = new ArrayList<TopicPartition>();
                        for (var partition : partitions) {
                            Long offset;
                            synchronized (this.offsets) {
                                offset = this.offsets.get(partition.partition());
                            }
                            if (offset == null) { // new partition
                                newPartitions.add(partition);
                                if (config.offset().right() != null) {
                                    var resetTo = Objects.requireNonNull(config.offset().right());
                                    if (resetTo.equals("earliest")) {
                                        consumer.seekToBeginning(List.of(partition));
                                    } else if (resetTo.equals("latest")) {
                                        consumer.seekToEnd(List.of(partition));
                                    } else {
                                        throw new IllegalArgumentException("Expected `earliest` or `latest` or some duration value, but received: " + resetTo);
                                    }
                                } else if (config.offset().left() != null) {
                                    var resetToDuration = Objects.requireNonNull(config.offset().left());
                                    var resetTo = Instant.now().minus(resetToDuration).toEpochMilli();
                                    var endOffset = consumer.endOffsets(List.of(partition)).get(partition);
                                    var resetToOffset = consumer.offsetsForTimes(Map.of(partition, resetTo)).get(partition);
                                    if (resetToOffset == null) {
                                        logger.debug("Kafka Consumer '{}' found no records newer than {} in partition {}, seeking to end", consumerPrefix, resetToDuration, partition);
                                        consumer.seek(partition, endOffset);
                                    } else {
                                        consumer.seek(partition, resetToOffset.offset());
                                    }
                                }
                            } else {
                                var epoch = worker.epochs.get(partition);
                                if (epoch == null) {
                                    consumer.seek(partition, offset + 1);
                                } else {
                                    consumer.seek(partition, new OffsetAndMetadata(offset + 1, Optional.of(epoch), ""));
                                }
                            }
                        }
                        for (var partition : newPartitions) {
                            var position = consumer.position(partition);
                            synchronized (this.offsets) {
                                this.offsets.set(partition.partition(), position - 1);
                            }
                        }
                    }

                    if (partitions.isEmpty()) {
                        this.awaitDelay(Math.min(1000, retryDelayMillis(worker)));
                        continue;
                    }
                    logger.trace("Kafka Consumer '{}' polling...", consumerPrefix);

                    ConsumerRecords<K, V> records;
                    try {
                        var retryDelay = retryDelayMillis(worker);
                        var pollTimeout = config.pollTimeout();
                        if (retryDelay != Long.MAX_VALUE && Duration.ofMillis(retryDelay).compareTo(pollTimeout) < 0) {
                            pollTimeout = Duration.ofMillis(retryDelay);
                        }
                        records = consumer.poll(pollTimeout);
                        this.savePollOffsets(consumer, records, partitions, worker);
                    } catch (OffsetOutOfRangeException e) {
                        var invalid = new HashSet<>(e.offsetOutOfRangePartitions().keySet());
                        invalid.retainAll(partitions);
                        if (invalid.isEmpty()) {
                            throw e;
                        }
                        for (var partition : invalid) {
                            var state = worker.retries.computeIfAbsent(partition,
                                ignored -> new PartitionRetry(config.backoffTimeout().toMillis()));
                            state.probing = false;
                            // Without the original epoch a numeric seek could hide a divergent log.
                            // Keep that partition isolated rather than silently accepting lost data.
                            state.requiresOperator = e instanceof LogTruncationException && !worker.epochs.containsKey(partition);
                            state.nextRetryAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(state.delay);
                            logger.warn("Kafka Consumer '{}' isolating partition {} at offset {} with auto.offset.reset={}; retry backoff {} ms, requires operator: {}",
                                consumerPrefix, partition, e.offsetOutOfRangePartitions().get(partition),
                                config.driverProperties().getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"), state.delay, state.requiresOperator, e);
                            state.delay = nextBackoff(state.delay);
                        }
                        // Reassign on the next round and restore every healthy checkpoint: a failed poll
                        // may have advanced internal fetch positions without returning those records.
                        isFirstAssign = true;
                        continue;
                    }
                    if (isFirstPoll) {
                        logger.info("Kafka Consumer '{}' first poll in {}",
                            consumerPrefix, TimeUtils.tookForLogging(started));
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

                    for (var partition : records.partitions()) {
                        worker.retries.remove(partition);
                    }
                    handler.handle(records, consumer, false);
                    synchronized (this.offsets) {
                        for (var partition : records.partitions()) {
                            var partitionRecords = records.records(partition);
                            var lastRecord = partitionRecords.get(partitionRecords.size() - 1);
                            this.offsets.set(partition.partition(), lastRecord.offset());
                            if (lastRecord.leaderEpoch().isPresent()) {
                                worker.epochs.put(partition, lastRecord.leaderEpoch().get());
                            } else {
                                worker.epochs.remove(partition);
                            }
                        }
                    }
                    if (!records.isEmpty()) {
                        this.refreshLag(consumer, partitions);
                    }

                    worker.backoff = config.backoffTimeout().toMillis();
                } finally {
                    Context.clear();
                }
            }
        } catch (WakeupException e) {
            if (this.isRunning()) {
                logger.warn("Kafka Consumer '{}' was unexpectedly woken up", consumerPrefix, e);
                retry = true;
            }
        } catch (Exception e) {
            logger.error("Kafka Consumer '{}' poll loop got unhandled exception", consumerPrefix, e);
            retry = true;
        } finally {
            consumers.remove(consumer);
            Context.clear();
        }
        if (retry) {
            this.awaitBackoff(worker);
        }
    }

    // Accessed only by the owning worker; retained across consumer recreation.
    private static final class WorkerState {
        private long backoff;
        private final Map<TopicPartition, PartitionRetry> retries = new HashMap<>();
        private final Map<TopicPartition, Integer> epochs = new HashMap<>();

        private WorkerState(long backoff) { this.backoff = backoff; }
    }

    private static final class PartitionRetry {
        private long delay;
        private long nextRetryAt;
        private boolean probing;
        private boolean requiresOperator;

        private PartitionRetry(long delay) { this.delay = delay; }
    }

    private static long nextBackoff(long delay) {
        return delay >= 30000 ? 60000 : delay * 2;
    }

    private long retryDelayMillis(WorkerState worker) {
        var delay = Long.MAX_VALUE;
        var now = System.nanoTime();
        for (var retry : worker.retries.values()) {
            if (!retry.probing && !retry.requiresOperator) {
                delay = Math.min(delay, Math.max(1, TimeUnit.NANOSECONDS.toMillis(retry.nextRetryAt - now)));
            }
        }
        return delay;
    }

    private void awaitBackoff(WorkerState worker) {
        this.awaitDelay(worker.backoff);
        if (this.isRunning()) {
            worker.backoff = nextBackoff(worker.backoff);
        }
    }

    private void awaitDelay(long timeout) {
        synchronized (backoffMonitor) {
            var remaining = TimeUnit.MILLISECONDS.toNanos(timeout);
            var deadline = System.nanoTime() + remaining;
            try {
                while (this.isRunning() && remaining > 0) {
                    TimeUnit.NANOSECONDS.timedWait(backoffMonitor, remaining);
                    remaining = deadline - System.nanoTime();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void savePollOffsets(Consumer<K, V> consumer, ConsumerRecords<K, V> records, List<TopicPartition> partitions, WorkerState worker) {
        var starts = new HashMap<TopicPartition, Long>();
        for (var partition : records.partitions()) {
            var partitionRecords = records.records(partition);
            if (!partitionRecords.isEmpty()) {
                var firstRecord = partitionRecords.get(0);
                starts.put(partition, firstRecord.offset());
                if (firstRecord.leaderEpoch().isPresent()) {
                    worker.epochs.put(partition, firstRecord.leaderEpoch().get());
                } else {
                    worker.epochs.remove(partition);
                }
            }
        }
        var changes = new HashMap<TopicPartition, PositionChange>();
        // Anchor returned records before any position lookup can fail for an empty partition.
        this.savePollOffsets(starts, changes);
        for (var partition : partitions) {
            if (!starts.containsKey(partition)) {
                try {
                    var position = consumer.position(partition, Duration.ZERO);
                    this.savePollOffsets(Map.of(partition, position), changes);
                    if (changes.containsKey(partition)) {
                        worker.epochs.remove(partition);
                    }
                } catch (TimeoutException ignored) {
                    // A pending reset has no resolved position and returned no records for this partition.
                }
            }
        }
        if (!changes.isEmpty()) {
            this.reportPositionChanges(consumer, changes);
        }
    }

    private void savePollOffsets(Map<TopicPartition, Long> starts, Map<TopicPartition, PositionChange> changes) {
        synchronized (this.offsets) {
            for (var entry : starts.entrySet()) {
                var partition = entry.getKey();
                var position = entry.getValue();
                var previous = this.offsets.get(partition.partition());
                this.offsets.set(partition.partition(), position - 1);
                if (previous != null && previous + 1 != position) {
                    changes.put(partition, new PositionChange(previous + 1, position));
                }
            }
        }
    }

    private void reportPositionChanges(Consumer<K, V> consumer, Map<TopicPartition, PositionChange> changes) {
        try {
            var deadline = System.nanoTime() + POSITION_CHANGE_TIMEOUT.toNanos();
            var beginnings = consumer.beginningOffsets(changes.keySet(), POSITION_CHANGE_TIMEOUT);
            var warnings = new HashMap<TopicPartition, PositionChange>();
            for (var entry : changes.entrySet()) {
                var partition = entry.getKey();
                var change = entry.getValue();
                var beginning = beginnings.get(partition);
                if (change.current() < change.previous() || change.previous() < beginning) {
                    warnings.put(partition, change);
                }
            }
            if (!warnings.isEmpty()) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new TimeoutException("Read position diagnostics timeout");
                }
                var ends = consumer.endOffsets(warnings.keySet(), Duration.ofNanos(remaining));
                for (var entry : warnings.entrySet()) {
                    var partition = entry.getKey();
                    var change = entry.getValue();
                    logger.warn("Kafka Consumer '{}' read position changed for partition {} from {} to {}: available offsets [{}, {})",
                        consumerPrefix, partition, change.previous(), change.current(), beginnings.get(partition), ends.get(partition));
                }
            }
        } catch (WakeupException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Kafka Consumer '{}' failed to inspect changed read positions {}", consumerPrefix, changes, e);
        }
    }

    private record PositionChange(long previous, long current) {}

    private void refreshLag(Consumer<K, V> consumer, List<TopicPartition> partitions) {
        try {
            var endOffsets = consumer.endOffsets(partitions);
            var lag = new HashMap<TopicPartition, Long>();
            synchronized (this.offsets) {
                for (var entry : endOffsets.entrySet()) {
                    var currentOffset = this.offsets.get(entry.getKey().partition());
                    if (currentOffset != null) {
                        lag.put(entry.getKey(), entry.getValue() - currentOffset - 1);
                    }
                }
            }
            lag.forEach(this.telemetry::reportLag);
        } catch (WakeupException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Kafka Consumer '{}' failed to refresh lag", consumerPrefix, e);
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
                synchronized (this.offsets) {
                    for (int i = this.offsets.size(); i < newPartitions.size(); i++) {
                        this.offsets.add(null);
                    }
                    this.partitions.set(newPartitions.stream().map(p -> new TopicPartition(p.topic(), p.partition())).toList());
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
            return new ConsumerWrapper<>(realConsumer, keyDeserializer, valueDeserializer);
        } catch (Exception e) {
            logger.error("Kafka Consumer '{}' failed to start in assign mode, due to: {}", consumerPrefix, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void init() {
        var threads = this.threads;
        if (threads > 0) {
            if (this.topic != null) {
                executorService = Executors.newFixedThreadPool(threads, new NamedThreadFactory(this.topic));
                for (int i = 0; i < threads; i++) {
                    var number = i;
                    executorService.execute(() -> {
                        while (this.isRunning()) {
                            logger.debug("Kafka Consumer '{}' starting in assign mode...", consumerPrefix);
                            final long started = TimeUtils.started();

                            var consumer = initializeConsumer();
                            if (consumer != null) {
                                launchPollLoop(consumer, number, started);
                            } else {
                                this.awaitBackoff(workers.computeIfAbsent(number,
                                    ignored -> new WorkerState(config.backoffTimeout().toMillis())));
                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public void release() {
        if (isActive.compareAndSet(true, false)) {
            logger.debug("Kafka Consumer '{}' stopping...", consumerPrefix);
            var started = System.nanoTime();

            this.wakeupConsumers();
            if (executorService != null) {
                if (!shutdownExecutorService(executorService, config.shutdownWait())) {
                    logger.warn("Kafka Consumer '{}' failed completing graceful shutdown in {}", consumerPrefix, config.shutdownWait());
                }
            }

            logger.info("Kafka Consumer '{}' stopped in {}", consumerPrefix, TimeUtils.tookForLogging(started));
        }
    }

    private boolean isRunning() {
        return isActive.get();
    }

    private void wakeupConsumers() {
        synchronized (backoffMonitor) {
            backoffMonitor.notifyAll();
        }
        synchronized (consumers) {
            for (var consumer : consumers) {
                consumer.wakeup();
            }
        }
    }

    private boolean shutdownExecutorService(ExecutorService executorService, Duration shutdownAwait) {
        boolean terminated = executorService.isTerminated();
        if (!terminated) {
            executorService.shutdown();
            try {
                logger.debug("Kafka Consumer '{}' awaiting graceful shutdown...", consumerPrefix);
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
