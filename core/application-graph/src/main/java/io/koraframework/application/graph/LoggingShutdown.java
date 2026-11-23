package io.koraframework.application.graph;

/**
 * Stops the logging backend once {@link KoraApplication} has nothing more to log: at the end of the shutdown hook
 * and right before the JVM exits on initialization failure, so asynchronous appenders get to flush their queues.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 */
public interface LoggingShutdown {

    void stop();
}
