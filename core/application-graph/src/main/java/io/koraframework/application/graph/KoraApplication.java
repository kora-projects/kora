package io.koraframework.application.graph;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class KoraApplication {
    private static final long SHUTDOWN_INIT_AWAIT_MILLIS = Long.parseLong(System.getProperty("kora.application.shutdownInitAwaitMillis", "10000"));

    private KoraApplication() {
        throw new IllegalStateException("KoraApplication is a utility class and cannot be instantiated");
    }

    public static void run(Supplier<ApplicationGraphDraw> supplier) {
        run(supplier, true);
    }

    public static void run(Supplier<ApplicationGraphDraw> supplier, boolean keepAlive) {
        var initStart = System.nanoTime();
        var keepAliveLock = new ReentrantLock();
        var condition = keepAliveLock.newCondition();
        var initDone = new CountDownLatch(1);
        var state = new State();

        // the hook is registered before init, so SIGTERM during a slow startup still releases what was initialized:
        // it waits for init to finish (on failure the graph releases its initialized nodes itself) and then releases
        var thread = new Thread(() -> {
            try {
                if (initDone.getCount() > 0) {
                    // System.exit() called from a Lifecycle.init or a factory blocks the init thread forever, so wait
                    // only when the exit came from a signal, and never longer than the timeout (Ctrl+C on a hung startup)
                    if (!isSignalShutdown()) {
                        return;
                    }
                    if (!initDone.await(SHUTDOWN_INIT_AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                        LoggerFactory.getLogger(KoraApplication.class).warn("Application was not initialized in {}ms after shutdown signal, exiting without release", SHUTDOWN_INIT_AWAIT_MILLIS);
                        stopLogging();
                        return;
                    }
                }
            } catch (InterruptedException e) {
                return;
            }
            var initializedGraph = state.graph;
            var logger = state.logger;
            if (initializedGraph == null || logger == null) {
                return; // init failed, main thread has already logged it and stopped logging
            }
            // release runs inside the hook itself: the JVM only guarantees it waits for
            // registered shutdown hooks to finish, not for arbitrary other application threads,
            // so signalling the condition from here without releasing first would let the JVM
            // halt before release() has actually completed
            try {
                logger.info("Application shutdown...");
                var releaseStart = System.nanoTime();
                initializedGraph.release();
                var releaseTook = Duration.ofNanos(System.nanoTime() - releaseStart).toMillis();
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
                stopLogging();
            }
        });
        thread.setName("kora-shutdown");
        Runtime.getRuntime().addShutdownHook(thread);

        try {
            var graphDraw = supplier.get();
            var logger = LoggerFactory.getLogger(graphDraw.getRoot());
            state.logger = logger;
            logger.debug("Application initializing...");
            state.graph = graphDraw.init();
            var initTook = Duration.ofNanos(System.nanoTime() - initStart).toMillis();
            try {
                var uptimeTook = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
                logger.info("Application initialized in {}ms (JVM running for {}s)", initTook, uptimeTook);
            } catch (Throwable ex) {
                logger.info("Application initialized in {}ms", initTook);
            }
        } catch (Throwable e) {
            state.graph = null;
            try {
                var logger = state.logger != null ? state.logger : LoggerFactory.getLogger(KoraApplication.class);
                logger.error("Application initializing failed with error", e);
                e.printStackTrace();
            } finally {
                stopLogging();
                initDone.countDown();
            }
            System.exit(-1);
            return;
        }
        initDone.countDown();

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

    // the JDK runs signal handlers (SIGTERM, SIGINT, SIGHUP) on a "SIG<name> handler" thread that calls System.exit
    private static boolean isSignalShutdown() {
        for (var t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("SIG") && t.getName().endsWith(" handler")) {
                return true;
            }
        }
        return false;
    }

    private static void stopLogging() {
        try {
            for (var loggingShutdown : ServiceLoader.load(LoggingShutdown.class, KoraApplication.class.getClassLoader())) {
                loggingShutdown.stop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static final class State {
        volatile @Nullable Logger logger;
        volatile @Nullable InitializedGraph graph;
    }
}
