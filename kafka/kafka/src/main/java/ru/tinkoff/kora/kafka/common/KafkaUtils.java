package ru.tinkoff.kora.kafka.common;

import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class KafkaUtils {
    public static String getConsumerPrefix(KafkaConsumerConfig config) {
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

    public static class NamedThreadFactory implements ThreadFactory {
        private static final String CONSUMER_PREFIX = "kafka-consumer-";

        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public NamedThreadFactory(String prefix) {
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
