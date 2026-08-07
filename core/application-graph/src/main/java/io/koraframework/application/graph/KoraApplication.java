package io.koraframework.application.graph;

import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class KoraApplication {

    private KoraApplication() {
        throw new IllegalStateException("KoraApplication is a utility class and cannot be instantiated");
    }

    public static void run(Supplier<ApplicationGraphDraw> supplier) {
        var initStart = System.nanoTime();
        var graphDraw = supplier.get();
        var logger = LoggerFactory.getLogger(graphDraw.getRoot());
        logger.debug("Application initializing...");

        var lock = new ReentrantLock();
        var condition = lock.newCondition();
        InitializedGraph graph;
        try {
            graph = graphDraw.init();
            var initEnd = System.nanoTime();
            var initTook = ((initEnd - initStart) / 1000);
            try {
                var uptimeTook = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
                logger.info("Application initialized in {}ms (JVM running for {}s)", initTook, uptimeTook);
            } catch (Throwable ex) {
                logger.info("Application initialized in {}ms", initTook);
            }
        } catch (Exception e) {
            logger.error("Application initializing failed with error", e);
            e.printStackTrace();
            try {
                Thread.sleep(100);// so async logger is able to write exception to log
            } catch (InterruptedException ignore) {}
            System.exit(-1);
            return;
        }

        var initializedGraph = graph;
        var thread = new Thread(() -> {
            // release runs inside the hook itself: the JVM only guarantees it waits for
            // registered shutdown hooks to finish, not for arbitrary other application threads,
            // so signalling the condition from here without releasing first would let the JVM
            // halt before release() has actually completed
            try {
                logger.info("Application shutdown...");
                var releaseStart = System.nanoTime();
                initializedGraph.release();
                var releaseTook = ((System.nanoTime() - releaseStart) / 1000);
                logger.info("Application released in {}ms", releaseTook);
            } catch (Exception e) {
                // System.exit() from within a shutdown hook can deadlock the JVM, so just log here
                logger.error("Application release error", e);
            } finally {
                lock.lock();
                condition.signalAll();
                lock.unlock();
            }
        });
        thread.setName("kora-shutdown");
        Runtime.getRuntime().addShutdownHook(thread);

        lock.lock();
        try {
            condition.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
}
