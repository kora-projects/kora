package io.koraframework.application.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class KoraApplication {

    private KoraApplication() {
        throw new IllegalStateException("KoraApplication is a utility class and cannot be instantiated");
    }

    public static void run(Supplier<ApplicationGraphDraw> supplier) {
        run(supplier, true);
    }

    public static void run(Supplier<ApplicationGraphDraw> supplier, boolean keepAlive) {
        var initStart = System.nanoTime();
        var graphDraw = supplier.get();
        LoggerFactory.getILoggerFactory();

        var keepAliveLock = new ReentrantLock();
        var condition = keepAliveLock.newCondition();
        InitializedGraph graph;
        Logger logger;

        try {
            graph = graphDraw.init();
            var initEnd = System.nanoTime();
            var initTook = (initEnd - initStart) / 1_000_000;
            logger = LoggerFactory.getLogger(graphDraw.getRoot());
            logger.debug("Application initializing...");

            if (Boolean.getBoolean("kora.jmx.uptime.enabled")) {
                try {
                    var uptimeTook = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
                    logger.info("Application initialized in {}ms (JVM running for {}s)", initTook, uptimeTook);
                } catch (Throwable ex) {
                    logger.info("Application initialized in {}ms", initTook);
                }
            } else {
                logger.info("Application initialized in {}ms", initTook);
            }
        } catch (Exception e) {
            logger = LoggerFactory.getLogger(graphDraw.getRoot());
            logger.error("Application initializing failed with error", e);
            e.printStackTrace();
            try {
                Thread.sleep(100); // so async logger is able to write exception to log
            } catch (InterruptedException ignore) {}
            System.exit(-1);
            return;
        }

        var initializedGraph = graph;
        var thread = new Thread(new ShutdownTask(initializedGraph, keepAlive, keepAliveLock, condition, logger));
        thread.setName("kora-shutdown");
        Runtime.getRuntime().addShutdownHook(thread);

        if (Boolean.getBoolean("kora.exit.after.init")) {
            System.exit(0);
        }

        if (keepAlive) {
            keepAliveLock.lock();
            try {
                condition.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                keepAliveLock.unlock();
            }
        }
    }

    private static final class ShutdownTask implements Runnable {
        private final InitializedGraph initializedGraph;
        private final boolean keepAlive;
        private final ReentrantLock keepAliveLock;
        private final Condition condition;
        private final Logger logger;

        public ShutdownTask(InitializedGraph initializedGraph, boolean keepAlive, ReentrantLock keepAliveLock, Condition condition, Logger logger) {
            this.initializedGraph = initializedGraph;
            this.keepAlive = keepAlive;
            this.keepAliveLock = keepAliveLock;
            this.condition = condition;
            this.logger = logger;
        }

        @Override
        public void run() {
            // release runs inside the hook itself: the JVM only guarantees it waits for
            // registered shutdown hooks to finish, not for arbitrary other application threads,
            // so signalling the condition from here without releasing first would let the JVM
            // halt before release() has actually completed
            try {
                logger.info("Application shutdown...");
                var releaseStart = System.nanoTime();
                initializedGraph.release();
                var releaseTook = (System.nanoTime() - releaseStart) / 1_000_000;
                logger.info("Application released in {}ms", releaseTook);
            } catch (Exception e) {
                // System.exit() from within a shutdown hook can deadlock the JVM, so just log here
                logger.error("Application release error", e);
            } finally {
                if (keepAlive) {
                    keepAliveLock.lock();
                    condition.signalAll();
                    keepAliveLock.unlock();
                }
            }
        }
    }
}
