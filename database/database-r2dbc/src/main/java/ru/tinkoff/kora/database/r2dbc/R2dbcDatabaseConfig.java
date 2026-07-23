package ru.tinkoff.kora.database.r2dbc;

import io.r2dbc.pool.ConnectionPoolConfiguration;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;
import java.time.Duration;
import java.util.Map;

/**
 * <b>Русский</b>: Конфигурация описывающая соединения к R2dbc базе данных.
 * <hr>
 * <b>English</b>: Configuration describing connections to the R2dbc database.
 *
 * @see R2dbcRepository
 */
@ConfigValueExtractor
public interface R2dbcDatabaseConfig {
    /**
     * @return R2DBC URL for connecting to the database.
     */
    String r2dbcUrl();

    /**
     * @return Username for the connection.
     */
    String username();

    /**
     * @return User password for the connection.
     */
    String password();

    /**
     * @return Connection pool name.
     */
    String poolName();

    /**
     * @return Maximum time to acquire a connection from the pool.
     */
    default Duration connectionTimeout() {
        return Duration.ofSeconds(10);
    }

    /**
     * @return Maximum time to create a new physical connection.
     */
    default Duration connectionCreateTimeout() {
        return Duration.ofSeconds(30);
    }

    /**
     * @return Maximum time to execute a query on the database.
     */
    @Nullable
    Duration statementTimeout();

    /**
     * @return Maximum idle time for a connection.
     */
    default Duration idleTimeout() {
        return Duration.ofMinutes(10);
    }

    /**
     * @return Maximum lifetime of a connection, zero means no limit.
     */
    default Duration maxLifetime() {
        return ConnectionPoolConfiguration.NO_TIMEOUT;
    }

    /**
     * @return Maximum number of attempts to acquire a connection.
     */
    default int acquireRetry() {
        return 3;
    }

    /**
     * @return Maximum connection pool size.
     */
    default int maxPoolSize() {
        return 10;
    }

    /**
     * @return Minimum number of idle ready connections in the pool.
     */
    default int minIdle() {
        return 0;
    }

    /**
     * @return Whether to enable the readiness probe for the database connection.
     */
    default boolean readinessProbe() {
        return false;
    }

    /**
     * @return Additional R2DBC connection options passed to the driver.
     */
    default Map<String, String> options() {
        return Map.of();
    }

    /**
     * @return Telemetry configuration of logging, metrics and tracing for database queries.
     */
    TelemetryConfig telemetry();
}
