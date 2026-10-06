package io.koraframework.kafka.common.consumer.containers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class KafkaContainerUtils {

    private static final long MAX_BACKOFF_MILLIS = 60_000;

    private KafkaContainerUtils() {}

    /**
     * Waits for the given time or until the container is stopped
     *
     * @return true if the container was stopped while waiting
     */
    static boolean awaitStop(CountDownLatch stopSignal, long millis) {
        try {
            return stopSignal.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // interrupt flag is cleared like Thread.sleep did, otherwise next poll throws InterruptException and loop spins without pause
            return stopSignal.getCount() == 0;
        }
    }

    static void increaseBackoff(AtomicLong backoffTimeout) {
        var current = backoffTimeout.get();
        if (current < MAX_BACKOFF_MILLIS) {
            backoffTimeout.set(Math.min(current * 2, MAX_BACKOFF_MILLIS));
        }
    }

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
