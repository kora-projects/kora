package ru.tinkoff.kora.database.vertx;

import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnectOptions;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;
import java.time.Duration;
import java.util.Objects;

/**
 * <b>Русский</b>: Конфигурация описывающая соединения к Vertx базе данных.
 * <hr>
 * <b>English</b>: Configuration describing connections to the Vertx database.
 *
 * @see VertxRepository
 */
@ConfigValueExtractor
public interface VertxDatabaseConfig {

    /**
     * @return Connection URI for the database.
     */
    String connectionUri();

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
     * @return Maximum time to establish a physical connection.
     */
    default Duration connectionTimeout() {
        return Duration.ofSeconds(10);
    }

    /**
     * @return Maximum idle time for a connection.
     */
    default Duration idleTimeout() {
        return Duration.ofMinutes(10);
    }

    /**
     * @return Maximum time to acquire a connection from the pool, connection timeout is used when not specified.
     */
    @Nullable
    Duration acquireTimeout();

    /**
     * @return Maximum connection pool size.
     */
    default int maxPoolSize() {
        return 10;
    }

    /**
     * @return Whether to cache prepared statements.
     */
    default boolean cachePreparedStatements() {
        return true;
    }

    /**
     * @return Whether to enable the readiness probe for the database connection.
     */
    default boolean readinessProbe() {
        return false;
    }

    /**
     * @return Maximum time to wait for a connection check at service startup, no check is performed when not specified.
     */
    @Nullable
    Duration initializationFailTimeout();

    static SqlConnectOptions toPgConnectOptions(VertxDatabaseConfig config) {
        var options = SqlConnectOptions.fromUri(config.connectionUri());

                options
                .setCachePreparedStatements(config.cachePreparedStatements())
                .setUser(config.username())
                .setPassword(config.password())
                .setConnectTimeout(Math.toIntExact(config.connectionTimeout().toMillis()))
                .setIdleTimeout(Math.toIntExact(config.idleTimeout().toMillis()))
                .setMetricsName(config.poolName());
        return options;
    }

    static PoolOptions toPgPoolOptions(VertxDatabaseConfig config) {
        return new PoolOptions()
                .setIdleTimeout(Math.toIntExact(config.idleTimeout().toMillis()))
                .setConnectionTimeout(Math.toIntExact(Objects.requireNonNullElse(config.acquireTimeout(), config.connectionTimeout()).toMillis()))
                .setName(config.poolName())
                .setMaxSize(config.maxPoolSize());
    }

    /**
     * @return Telemetry configuration of logging, metrics and tracing for database queries.
     */
    TelemetryConfig telemetry();
}
