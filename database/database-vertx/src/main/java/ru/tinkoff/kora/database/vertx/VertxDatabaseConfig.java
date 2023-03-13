package ru.tinkoff.kora.database.vertx;

import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;

import javax.annotation.Nullable;
import java.time.Duration;

public record VertxDatabaseConfig(
    String username,
    String password,
    String host,
    int port,
    String database,
    String poolName,
    Duration connectionTimeout,
    Duration idleTimeout,
    Duration acquireTimeout,
    int maxPoolSize,
    boolean cachePreparedStatements) {

    public VertxDatabaseConfig(
        String username,
        String password,
        String host,
        int port,
        String database,
        String poolName,
        @Nullable Duration connectionTimeout,
        @Nullable Duration idleTimeout,
        @Nullable Duration acquireTimeout,
        @Nullable Integer maxPoolSize,
        @Nullable Boolean cachePreparedStatements) {
        this(
            username,
            password,
            host,
            port,
            database,
            poolName,
            defaultConnectionTimeout(connectionTimeout),
            idleTimeout != null ? idleTimeout : Duration.ofMinutes(10),
            acquireTimeout != null ? acquireTimeout : defaultConnectionTimeout(connectionTimeout),
            maxPoolSize != null ? maxPoolSize : 10,
            cachePreparedStatements != null ? cachePreparedStatements : true
        );
    }

    private static Duration defaultConnectionTimeout(Duration connectionTimeout) {
        return connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(30);
    }

    public PgConnectOptions toPgConnectOptions() {
        return new PgConnectOptions()
            .setMetricsName(this.poolName)
            .setHost(this.host)
            .setPort(this.port)
            .setDatabase(this.database)
            .setUser(this.username)
            .setPassword(this.password)
            .setConnectTimeout(Math.toIntExact(this.connectionTimeout.toMillis()))
            .setIdleTimeout(Math.toIntExact(this.idleTimeout.toMillis()))
            .setCachePreparedStatements(this.cachePreparedStatements);
    }

    public PoolOptions toPgPoolOptions() {
        return new PoolOptions()
            .setIdleTimeout(Math.toIntExact(this.idleTimeout.toMillis()))
            .setConnectionTimeout(Math.toIntExact(this.connectionTimeout.toMillis()))
            .setName(this.poolName)
            .setMaxSize(this.maxPoolSize);
    }
}
