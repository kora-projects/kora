package ru.tinkoff.kora.kafka.common.consumer.containers;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.kafka.common.KafkaUtils.NamedThreadFactory;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static ru.tinkoff.kora.kafka.common.KafkaUtils.getConsumerPrefix;

public final class KafkaSubscribeConsumerContainer<K, V> implements Lifecycle {

    private final static Logger logger = LoggerFactory.getLogger(KafkaSubscribeConsumerContainer.class);

    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicLong backoffTimeout;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;
    private volatile ExecutorService executorService;

    private final BaseKafkaRecordsHandler<K, V> handler;
    private final Set<Consumer<K, V>> consumers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final KafkaConsumerConfig config;
    private final String consumerPrefix;
    private final boolean commitAllowed;

    public KafkaSubscribeConsumerContainer(
        KafkaConsumerConfig config,
        Deserializer<K> keyDeserializer,
        Deserializer<V> valueDeserializer,
        BaseKafkaRecordsHandler<K, V> handler
    ) {
        if (config.driverProperties().getProperty(CommonClientConfigs.GROUP_ID_CONFIG) == null) {
            throw new IllegalArgumentException("Group id is required for subscribe container");
        }
        this.handler = handler;
        this.backoffTimeout = new AtomicLong(config.backoffTimeout().toMillis());
        this.consumerPrefix = getConsumerPrefix(config);
        var autoCommit = config.driverProperties().getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);
        if (autoCommit == null) {
            config = config.withDriverPropertiesOverrides(Map.of(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false));
            this.commitAllowed = true;
        } else {
            this.commitAllowed = !Boolean.parseBoolean(autoCommit);
        }
        this.config = config;
        this.keyDeserializer = keyDeserializer;
        this.valueDeserializer = valueDeserializer;
    }


    public void launchPollLoop(long started) {
        while (isActive.get()) {
            final Consumer<K, V> consumer;
            try {
                consumer = this.buildConsumer();
            } catch (Exception e) {
                logger.error("Kafka Consumer '{}' initialization failed", consumerPrefix, e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    logger.error("Kafka Consumer '{}' error interrupting thread", consumerPrefix, ie);
                }
                continue;
            }

            try (consumer) {
                consumers.add(consumer);
                logger.info("Kafka Consumer '{}' started in {}",
                    consumerPrefix, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());

                boolean isFirstPoll = true;
                while (isActive.get()) {
                    try {
                        logger.trace("Kafka Consumer '{}' polling...", consumerPrefix);

                        var records = consumer.poll(config.pollTimeout());
                        if (isFirstPoll) {
                            logger.info("Kafka Consumer '{}' first poll in {}",
                                consumerPrefix, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                            isFirstPoll = false;
                        }

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

                        handler.handle(records, consumer, this.commitAllowed);
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
                Thread.interrupted();
            } finally {
                consumers.remove(consumer);
            }
        }
    }

    @Override
    public void init() {
        if (config.threads() > 0 && this.isActive.compareAndSet(false, true)) {
            logger.debug("Kafka Consumer '{}' starting...", consumerPrefix);
            final long started = System.nanoTime();

            executorService = Executors.newFixedThreadPool(config.threads(), new NamedThreadFactory(consumerPrefix));
            for (int i = 0; i < config.threads(); i++) {
                executorService.execute(() -> launchPollLoop(started));
            }

            logger.info("Started Kafka Consumer '{}' took {}", consumerPrefix, Duration.ofNanos(System.nanoTime() - started));
        }
    }

    @Override
    public void release() {
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
    }

    private Consumer<K, V> buildConsumer() {
        var consumer = new KafkaConsumer<>(this.config.driverProperties(), new ByteArrayDeserializer(), new ByteArrayDeserializer());
        try {
            if (config.topicsPattern() != null) {
                consumer.subscribe(config.topicsPattern());
            } else if (config.topics() != null) {
                consumer.subscribe(config.topics());
            }
        } catch (Exception e) {
            try {
                consumer.close();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }

        return new ConsumerWrapper<>(consumer, keyDeserializer, valueDeserializer);
    }
}
