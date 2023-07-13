package ru.tinkoff.kora.database.vertx;

import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Objects;

@ConfigValueExtractor
public interface VertxDatabaseConfig {
    String username();

    String password();

    String host();

    int port();

    String database();

    String poolName();

    default Duration connectionTimeout() {
        return Duration.ofSeconds(30);
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

    static PgConnectOptions toPgConnectOptions(VertxDatabaseConfig config) {
        return new PgConnectOptions()
            .setMetricsName(config.poolName())
            .setHost(config.host())
            .setPort(config.port())
            .setDatabase(config.database())
            .setUser(config.username())
            .setPassword(config.password())
            .setConnectTimeout(Math.toIntExact(config.connectionTimeout().toMillis()))
            .setIdleTimeout(Math.toIntExact(config.idleTimeout().toMillis()))
            .setCachePreparedStatements(config.cachePreparedStatements());
    }

    static PoolOptions toPgPoolOptions(VertxDatabaseConfig config) {
        return new PoolOptions()
            .setIdleTimeout(Math.toIntExact(config.idleTimeout().toMillis()))
            .setConnectionTimeout(Math.toIntExact(Objects.requireNonNullElse(config.acquireTimeout(), config.connectionTimeout()).toMillis()))
            .setName(config.poolName())
            .setMaxSize(config.maxPoolSize());
    }
}
