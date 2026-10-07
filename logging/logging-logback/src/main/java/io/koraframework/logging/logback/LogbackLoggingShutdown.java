package io.koraframework.logging.logback;

import ch.qos.logback.classic.LoggerContext;
import io.koraframework.application.graph.LoggingShutdown;
import org.slf4j.LoggerFactory;

/**
 * Stops the Logback {@link LoggerContext} on application shutdown, so {@link KoraAsyncAppender} drains its queue
 * within its max flush time instead of being cut off together with the JVM.
 */
public final class LogbackLoggingShutdown implements LoggingShutdown {

    @Override
    public void stop() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
        }
    }
}
