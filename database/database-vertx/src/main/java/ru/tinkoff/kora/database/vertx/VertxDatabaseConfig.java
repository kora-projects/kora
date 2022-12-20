package ru.tinkoff.kora.database.vertx;

import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;

import javax.annotation.Nullable;

public record VertxDatabaseConfig(
    String username,
    String password,
    String host,
    int port,
    String database,
    String poolName,
    int connectionTimeout,
    int idleTimeout,
    int acquireTimeout,
    int maxPoolSize) {

    public VertxDatabaseConfig(
        String username,
        String password,
        String host,
        int port,
        String database,
        String poolName,
        @Nullable Integer connectionTimeout,
        @Nullable Integer idleTimeout,
        @Nullable Integer acquireTimeout,
        @Nullable Integer maxPoolSize) {
        this(
            username,
            password,
            host,
            port,
            database,
            poolName,
            defaultConnectionTimeout(connectionTimeout),
            idleTimeout != null ? idleTimeout : 10 * 60 * 1000,
            acquireTimeout != null ? acquireTimeout : defaultConnectionTimeout(connectionTimeout),
            maxPoolSize != null ? maxPoolSize : 10
        );
    }

    private static int defaultConnectionTimeout(Integer connectionTimeout) {
        return connectionTimeout != null ? connectionTimeout : 30000;
    }

    public PgConnectOptions toPgConnectOptions() {
        return new PgConnectOptions()
            .setMetricsName(this.poolName)
            .setHost(this.host)
            .setPort(this.port)
            .setDatabase(this.database)
            .setUser(this.username)
            .setPassword(this.password)
            .setConnectTimeout(this.connectionTimeout)
            .setIdleTimeout(this.idleTimeout)
            .setCachePreparedStatements(true);
    }

    public PoolOptions toPgPoolOptions() {
        return new PoolOptions()
            .setIdleTimeout(this.idleTimeout)
            .setConnectionTimeout(this.connectionTimeout)
            .setName(this.poolName)
            .setMaxSize(this.maxPoolSize);
    }
}
