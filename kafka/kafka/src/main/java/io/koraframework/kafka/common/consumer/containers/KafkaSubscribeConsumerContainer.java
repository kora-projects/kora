package io.koraframework.kafka.common.consumer.containers;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.kafka.common.KafkaUtils.NamedThreadFactory;
import io.koraframework.kafka.common.consumer.ConsumerAwareRebalanceListener;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
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
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class KafkaSubscribeConsumerContainer<K, V> implements Lifecycle {

    private final Logger logger;

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
    private final String listenerName;
    private final String listenerImpl;
    private final boolean commitAllowed;

    public KafkaSubscribeConsumerContainer(String listenerName,
                                           String listenerImpl,
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
        this.telemetry = telemetry;
        this.listenerName = Objects.requireNonNull(listenerName);
        this.listenerImpl = Objects.requireNonNull(listenerImpl);
        var logger = LoggerFactory.getLogger(listenerImpl);
        this.logger = this.config.telemetry().logging().enabled() && logger.isErrorEnabled()
            ? logger
            : NOPLogger.NOP_LOGGER;
    }

    public void launchPollLoop(Consumer<K, V> consumer, String listenerName, long started) {
        try (consumer) {
            consumers.add(consumer);
            logger.info("{} started in {}", listenerName, TimeUtils.tookForLogging(started));

            boolean isFirstPoll = true;
            while (isActive.get()) {
                try {
                    logger.trace("{} polling...", listenerName);

                    var observation = this.telemetry.observePoll();
                    var records = consumer.poll(config.pollTimeout());
                    if (isFirstPoll) {
                        logger.info("{} first poll for '{}' records in {}",
                            listenerName, records.count(), TimeUtils.tookForLogging(started));
                        isFirstPoll = false;
                    }

                    handler.handle(observation, records, consumer, this.commitAllowed);
                    backoffTimeout.set(config.backoffTimeout().toMillis());
                } catch (WakeupException ignore) {
                } catch (Exception e) {
                    logger.error("{} got unhandled exception", listenerName, e);
                    try {
                        logger.debug("{} backing off for {}ms...", listenerName, backoffTimeout.get());
                        Thread.sleep(backoffTimeout.get());
                    } catch (InterruptedException ie) {
                        logger.error("{} error interrupting thread", listenerName, ie);
                    }
                    if (backoffTimeout.get() < 60000) {
                        backoffTimeout.set(backoffTimeout.get() * 2);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("{} poll loop got unhandled exception", listenerName, e);
        } finally {
            consumers.remove(consumer);
        }
    }

    @Override
    public void init() {
        if (config.threads() > 0 && this.isActive.compareAndSet(false, true)) {
            logger.debug("KafkaListener '{}' starting in subscribe mode...", listenerName);
            final long started = TimeUtils.started();

            executorService = Executors.newFixedThreadPool(config.threads(), new NamedThreadFactory(listenerName));
            CountDownLatch initLatch = new CountDownLatch(config.threads());

            for (int i = 0; i < config.threads(); i++) {
                var number = i;
                executorService.execute(() -> {
                    // infinite try to init in cycle, will go into infinite pool loop if init success
                    while (isActive.get()) {
                        var consumer = initializeConsumer();
                        if (consumer != null) {
                            initLatch.countDown();

                            var listenerName = (config.threads() == 1)
                                ? "KafkaListener '" + this.listenerName + "'"
                                : "KafkaListener-" + number + " '" + this.listenerName + "'";
                            launchPollLoop(consumer, listenerName, started);
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

    @Override
    public void release() {
        if (isActive.compareAndSet(true, false)) {
            logger.debug("KafkaListener '{}' stopping...", listenerName);
            final long started = TimeUtils.started();

            for (var consumer : consumers) {
                consumer.wakeup();
            }
            consumers.clear();
            if (executorService != null) {
                if (!shutdownExecutorService(executorService, config.shutdownWait())) {
                    logger.warn("KafkaListener '{}' failed completing graceful shutdown in {}",
                        listenerName, config.shutdownWait());
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

    @Nullable
    private Consumer<K, V> initializeConsumer() {
        try {
            return this.buildConsumer();
        } catch (Exception e) {
            logger.error("KafkaListener '{}' failed to start in subscribe mode, due to: {}",
                listenerName, e.getMessage(), e);
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                logger.error("KafkaListener '{}' error interrupting thread", listenerName, ie);
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
