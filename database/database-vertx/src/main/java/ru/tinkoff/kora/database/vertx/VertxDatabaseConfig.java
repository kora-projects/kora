package ru.tinkoff.kora.database.vertx;

import io.vertx.pgclient.PgConnectOptions;

import javax.annotation.Nullable;

public record VertxDatabaseConfig(
    String username,
    String password,
    String host,
    String database,
    String poolName,
    @Nullable
    HostRequirement hostRequirement,
    int connectionTimeout,
    int idleTimeout,
    int acquireTimeout,
    int maxPoolSize,
    int minPoolSize,
    long aliveBypassWindow) {

    public enum HostRequirement {
        PRIMARY, SECONDARY;
    }

    public VertxDatabaseConfig(
        String username,
        String password,
        String host,
        String database,
        String poolName,
        @Nullable HostRequirement hostRequirement,
        @Nullable Integer connectionTimeout,
        @Nullable Integer idleTimeout,
        @Nullable Integer acquireTimeout,
        @Nullable Integer maxPoolSize,
        @Nullable Integer minPoolSize,
        @Nullable Integer aliveBypassWindow) {
        this(
            username,
            password,
            host,
            database,
            poolName,
            hostRequirement,
            defaultConnectionTimeout(connectionTimeout),
            idleTimeout != null ? idleTimeout : 10 * 60 * 1000,
            acquireTimeout != null ? acquireTimeout : defaultConnectionTimeout(connectionTimeout),
            maxPoolSize != null ? maxPoolSize : 10,
            minPoolSize != null ? minPoolSize : 0,
            aliveBypassWindow == null ? 500 : aliveBypassWindow
        );
    }

    private static int defaultConnectionTimeout(Integer connectionTimeout) {
        return connectionTimeout != null ? connectionTimeout : 30000;
    }

    public PgConnectOptions toPgConnectOptions(String host, int port) {
        return new PgConnectOptions()
            .setMetricsName(this.poolName)
            .setHost(host)
            .setPort(port)
            .setDatabase(this.database)
            .setUser(this.username)
            .setPassword(this.password)
            .setConnectTimeout(this.connectionTimeout)
            .setIdleTimeout(this.idleTimeout)
            .setCachePreparedStatements(true);
    }
}
