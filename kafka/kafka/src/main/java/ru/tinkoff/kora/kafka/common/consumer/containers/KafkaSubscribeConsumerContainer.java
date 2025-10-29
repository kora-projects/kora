package ru.tinkoff.kora.kafka.common.consumer.containers;

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.kafka.common.KafkaUtils;
import ru.tinkoff.kora.kafka.common.KafkaUtils.NamedThreadFactory;
import ru.tinkoff.kora.kafka.common.consumer.ConsumerAwareRebalanceListener;
import ru.tinkoff.kora.kafka.common.consumer.KafkaListenerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class KafkaSubscribeConsumerContainer<K, V> implements Lifecycle {

    private final static Logger logger = LoggerFactory.getLogger(KafkaSubscribeConsumerContainer.class);

    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicLong backoffTimeout;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;
    private final KafkaConsumerTelemetry telemetry;
    private volatile ExecutorService executorService;

    private final BaseKafkaRecordsHandler<K, V> handler;
    private final Set<Consumer<K, V>> consumers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    @Nullable
    private final ConsumerAwareRebalanceListener rebalanceListener;
    private final KafkaListenerConfig config;
    private final String consumerPrefix;
    private final boolean commitAllowed;

    public KafkaSubscribeConsumerContainer(String consumerName,
                                           KafkaListenerConfig config,
                                           Deserializer<K> keyDeserializer,
                                           Deserializer<V> valueDeserializer,
                                           BaseKafkaRecordsHandler<K, V> handler,
                                           KafkaConsumerTelemetry telemetry,
                                           @Nullable ConsumerAwareRebalanceListener rebalanceListener) {
        if (config.driverProperties().get(CommonClientConfigs.GROUP_ID_CONFIG) == null) {
            throw new IllegalArgumentException("Group id is required for subscribe container");
        }

        this.handler = handler;
        this.rebalanceListener = rebalanceListener;
        var autoCommit = config.driverProperties().get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);
        if (autoCommit == null) {
            config = config.withDriverPropertiesOverrides(Map.of(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false));
            this.commitAllowed = true;
        } else {
            this.commitAllowed = !Boolean.parseBoolean(String.valueOf(autoCommit));
        }
        this.config = config;
        this.keyDeserializer = keyDeserializer;
        this.valueDeserializer = valueDeserializer;
        this.backoffTimeout = new AtomicLong(config.backoffTimeout().toMillis());
        if (consumerName == null || consumerName.isBlank()) {
            this.consumerPrefix = KafkaUtils.getConsumerPrefix(config);
        } else {
            this.consumerPrefix = consumerName;
        }
        this.telemetry = telemetry;
    }

    public void launchPollLoop(Consumer<K, V> consumer, long started) {
        try (consumer) {
            consumers.add(consumer);
            logger.info("Kafka Consumer '{}' started in {}", consumerPrefix, TimeUtils.tookForLogging(started));

            boolean isFirstPoll = true;
            while (isActive.get()) {
                try {
                    logger.trace("Kafka Consumer '{}' polling...", consumerPrefix);

                    var observation = this.telemetry.observePoll();
                    var records = consumer.poll(config.pollTimeout());
                    if (isFirstPoll) {
                        logger.info("Kafka Consumer '{}' first poll in {}",
                            consumerPrefix, TimeUtils.tookForLogging(started));
                        isFirstPoll = false;
                    }

                    handler.handle(observation, records, consumer, this.commitAllowed);
                    backoffTimeout.set(config.backoffTimeout().toMillis());
                } catch (WakeupException ignore) {
                } catch (Exception e) {
                    logger.error("Kafka Consumer '{}' got unhandled exception", consumerPrefix, e);
                    try {
                        Thread.sleep(backoffTimeout.get());
                    } catch (InterruptedException ie) {
                        logger.error("Kafka Consumer '{}' error interrupting thread", consumerPrefix, ie);
                    }
                    if (backoffTimeout.get() < 60000) {
                        backoffTimeout.set(backoffTimeout.get() * 2);
                    }
                    break;
                } finally {
                    Context.clear();
                }
            }
        } catch (Exception e) {
            logger.error("Kafka Consumer '{}' poll loop got unhandled exception", consumerPrefix, e);
        } finally {
            consumers.remove(consumer);
        }
    }

    @Override
    public void init() {
        if (config.threads() > 0 && this.isActive.compareAndSet(false, true)) {
            logger.debug("Kafka Consumer '{}' starting in subscribe mode...", consumerPrefix);
            final long started = TimeUtils.started();

            executorService = Executors.newFixedThreadPool(config.threads(), new NamedThreadFactory(consumerPrefix));
            for (int i = 0; i < config.threads(); i++) {
                executorService.execute(() -> {
                    while (isActive.get()) {
                        var consumer = initializeConsumer();
                        if (consumer != null) {
                            launchPollLoop(consumer, started);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void release() {
        if (isActive.compareAndSet(true, false)) {
            logger.debug("Kafka Consumer '{}' stopping...", consumerPrefix);
            final long started = TimeUtils.started();

            for (var consumer : consumers) {
                consumer.wakeup();
            }
            consumers.clear();
            if (executorService != null) {
                if (!shutdownExecutorService(executorService, config.shutdownWait())) {
                    logger.warn("Kafka Consumer '{}' failed completing graceful shutdown in {}", consumerPrefix, config.shutdownWait());
                }
            }

            logger.info("Kafka Consumer '{}' stopped in {}", consumerPrefix, TimeUtils.tookForLogging(started));
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

    @Nullable
    private Consumer<K, V> initializeConsumer() {
        try {
            return this.buildConsumer();
        } catch (Exception e) {
            logger.error("Kafka Consumer '{}' failed to start in subscribe mode, due to: {}", consumerPrefix, e.getMessage(), e);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                logger.error("Kafka Consumer '{}' error interrupting thread", consumerPrefix, ie);
            }
            return null;
        }
    }

    private Consumer<K, V> buildConsumer() {
        var consumer = new KafkaConsumer<>(this.config.driverProperties(), new ByteArrayDeserializer(), new ByteArrayDeserializer());
        try {
            if (config.topicsPattern() != null) {
                if (rebalanceListener != null) {
                    consumer.subscribe(config.topicsPattern(), new ConsumerRebalanceListener() {
                        @Override
                        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                            rebalanceListener.onPartitionsRevoked(consumer, partitions);
                        }

                        @Override
                        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                            rebalanceListener.onPartitionsAssigned(consumer, partitions);
                        }

                        @Override
                        public void onPartitionsLost(Collection<TopicPartition> partitions) {
                            rebalanceListener.onPartitionsLost(consumer, partitions);
                        }
                    });
                } else {
                    consumer.subscribe(config.topicsPattern());
                }
            } else if (config.topics() != null) {
                if (rebalanceListener != null) {
                    consumer.subscribe(config.topics(), new ConsumerRebalanceListener() {
                        @Override
                        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                            rebalanceListener.onPartitionsRevoked(consumer, partitions);
                        }

                        @Override
                        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                            rebalanceListener.onPartitionsAssigned(consumer, partitions);
                        }

                        @Override
                        public void onPartitionsLost(Collection<TopicPartition> partitions) {
                            rebalanceListener.onPartitionsLost(consumer, partitions);
                        }
                    });
                } else {
                    consumer.subscribe(config.topics());
                }
            }
        } catch (Exception e) {
            try {
                consumer.close();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        var driverMetrics = (KafkaClientMetrics) null;
        if (this.config.telemetry().metrics().driverMetrics()) {
            var metrics = new KafkaClientMetrics(consumer);
            metrics.bindTo(this.telemetry.meterRegistry());
            driverMetrics = metrics;
        }

        return new ConsumerWrapper<>(consumer, driverMetrics, keyDeserializer, valueDeserializer);
    }
}
