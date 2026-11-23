package io.koraframework.logging.logback;

import ch.qos.logback.classic.LoggerContext;
import io.koraframework.application.graph.KoraApplication;
import io.koraframework.application.graph.LoggingShutdown;
import org.slf4j.LoggerFactory;

/**
 * Stops the Logback context on shutdown, so {@link KoraAsyncAppender} flushes its queue, waiting at most for its
 * max flush time, instead of losing it when the JVM halts.
 * <p>
 * Called by {@link KoraApplication} once the graph is released. For applications not started with
 * {@link KoraApplication}, the shutdown hook registered by {@link KoraLogbackConfigurator} stops the context instead.
 */
public final class KoraLogbackShutdown implements LoggingShutdown {

    // ponytail: fixed grace period for other shutdown hooks, which run concurrently, to finish logging; make it
    // configurable if a hook ever needs longer
    static final long DELAY_MILLIS = 100;

    @Override
    public void shutdown() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            stop(context);
        }
    }

    static Thread hook(LoggerContext context) {
        var hook = new Thread(() -> {
            // the graph logs while it is released, KoraApplication stops the context itself once that is done
            if (!KoraApplication.isShutdownHookRegistered()) {
                stop(context);
            }
        });
        hook.setName("kora-logback-shutdown");
        return hook;
    }

    private static void stop(LoggerContext context) {
        try {
            Thread.sleep(DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        context.stop();
    }
}
