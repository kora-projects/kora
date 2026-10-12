package io.koraframework.application.graph;

/**
 * Stops the logging backend once {@link KoraApplication} has nothing more to log, so asynchronous appenders get to
 * flush what they still hold before the JVM halts.
 * <p>
 * Implementations are discovered with {@link java.util.ServiceLoader}.
 */
public interface LoggingShutdown {

    void shutdown();
}
