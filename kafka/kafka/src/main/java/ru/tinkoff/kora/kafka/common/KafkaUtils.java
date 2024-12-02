package ru.tinkoff.kora.kafka.common;

import jakarta.annotation.Nonnull;
import org.apache.kafka.clients.CommonClientConfigs;
import ru.tinkoff.kora.kafka.common.consumer.KafkaListenerConfig;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class KafkaUtils {

    private KafkaUtils() {}

    public static String getConsumerPrefix(KafkaListenerConfig config) {
        final Object groupId = config.driverProperties().get(CommonClientConfigs.GROUP_ID_CONFIG);
        if(groupId != null) {
            return groupId.toString();
        }

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
