package ru.tinkoff.kora.kafka.common.containers;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.kafka.common.KafkaConsumerFactory;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.containers.handlers.BaseKafkaRecordsHandler;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class KafkaConsumerContainer<K, V> implements Lifecycle {

    private final static Logger logger = LoggerFactory.getLogger(KafkaConsumerContainer.class);

    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private final AtomicLong backoffTimeout;
    private ExecutorService executorService;

    private final KafkaConsumerFactory<K, V> factory;
    private final BaseKafkaRecordsHandler<K, V> handler;
    private final Set<Consumer<K, V>> consumers = new HashSet<>();
    private final KafkaConsumerConfig config;
    private final boolean allowCommit;
    private final String consumerPrefix;

    public KafkaConsumerContainer(KafkaConsumerConfig config,
                                  Deserializer<K> keyDeserializer,
                                  Deserializer<V> valueDeserializer,
                                  BaseKafkaRecordsHandler<K, V> handler) {
        this.handler = handler;
        this.config = config;
        this.backoffTimeout = new AtomicLong(config.backoffTimeout().toMillis());
        this.consumerPrefix = getConsumerPrefix(config);

        if (config.driverProperties().getProperty(CommonClientConfigs.GROUP_ID_CONFIG) == null) {
            this.factory = KafkaConsumerFactory.assign(config, keyDeserializer, valueDeserializer);
            this.allowCommit = false;
        } else {
            if (config.driverProperties().getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG) == null) {
                config = config.withDriverPropertiesOverrides(Map.of(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false));
            }
            this.factory = KafkaConsumerFactory.subscribe(config, keyDeserializer, valueDeserializer);
            this.allowCommit = true;
        }
    }

    private static String getConsumerPrefix(KafkaConsumerConfig config) {
        if (config.topics() != null) {
            return String.join(";", config.topics());
        } else if (config.topicsPattern() != null) {
            return config.topicsPattern().toString();
        } else if (config.partitions() != null) {
            return String.join(";", config.partitions());
        } else {
            return "unknown";
        }
    }

    public void launchPollLoop() {
        Consumer<K, V> consumer = null;
        while (isActive.get()) {
            if (consumer == null) {
                try {
                    consumer = factory.buildConsumer();
                    consumers.add(consumer);
                } catch (WakeupException ignore) {
                } catch (KafkaException e) {
                    logger.error("Kafka Consumer '{}' failed initializing", consumerPrefix, e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        logger.error("Kafka Consumer '{}' error interrupting thread", consumerPrefix, ie);
                    }
                    continue;
                }
            }
            if (consumer != null) {
                while (isActive.get()) {
                    try {
                        var records = consumer.poll(config.pollTimeout());
                        handler.handle(records, consumer, allowCommit);
                        backoffTimeout.set(config.backoffTimeout().toMillis());
                    } catch (WakeupException ignore) {
                    } catch (Exception e) {
                        logger.error("Kafka Consumer '{}' got unhandled exception", consumerPrefix, e);
                        consumer.close();
                        consumers.remove(consumer);
                        consumer = null;
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
            }
        }
        if (consumer != null) {
            Thread.interrupted();
            consumer.close();
        }
    }

    @Override
    public Mono<Void> init() {
        return Mono.fromRunnable(() -> {
            if (config.threads() > 0) {
                logger.debug("Starting Kafka Consumer '{}'...", consumerPrefix);
                final long started = System.nanoTime();

                executorService = Executors.newFixedThreadPool(config.threads(), new NamedThreadFactory(consumerPrefix));
                for (int i = 0; i < config.threads(); i++) {
                    executorService.execute(this::launchPollLoop);
                }

                logger.info("Started Kafka Consumer '{}' took {}", consumerPrefix, Duration.ofNanos(System.nanoTime() - started));
            }
        });
    }

    @Override
    public Mono<Void> release() {
        return Mono.fromRunnable(() -> {
            if (isActive.compareAndSet(true, false)) {
                logger.debug("Stopping Kafka Consumer '{}'...", consumerPrefix);
                final long started = System.nanoTime();

                for (var consumer : consumers) {
                    consumer.wakeup();
                }
                consumers.clear();
                executorService.shutdownNow();

                logger.info("Stopped Kafka Consumer '{}' took {}", consumerPrefix, Duration.ofNanos(System.nanoTime() - started));
            }
        });
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private static final String CONSUMER_PREFIX = "kafka-consumer-";

        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String prefix) {
            namePrefix = prefix;
        }

        public Thread newThread(@Nonnull Runnable runnable) {
            var thread = new Thread(runnable, CONSUMER_PREFIX + namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
