package io.koraframework.kafka.common.consumer.containers;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class KafkaContainerUtils {

    private KafkaContainerUtils() {}

    public static class NamedThreadFactory implements ThreadFactory {

        private static final String CONSUMER_PREFIX = "kafka-listener-";

        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public NamedThreadFactory(String prefix) {
            namePrefix = prefix;
        }

        public Thread newThread(Runnable runnable) {
            var thread = new Thread(runnable, CONSUMER_PREFIX + namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
