package ru.tinkoff.kora.database.vertx;

import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnectOptions;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DatabaseTelemetryConfig;
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

    String connectionUri();

    String username();

    String password();

    String poolName();

    default Duration connectionTimeout() {
        return Duration.ofSeconds(10);
    }

    default Duration idleTimeout() {
        return Duration.ofMinutes(10);
    }

    @Nullable
    Duration acquireTimeout();

    default int maxPoolSize() {
        return 10;
    }

    default boolean cachePreparedStatements() {
        return true;
    }

    default boolean readinessProbe() {
        return false;
    }

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

    DatabaseTelemetryConfig telemetry();
}
