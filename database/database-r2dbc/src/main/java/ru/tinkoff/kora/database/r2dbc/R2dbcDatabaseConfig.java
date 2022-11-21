package ru.tinkoff.kora.database.r2dbc;

import javax.annotation.Nullable;

public record R2dbcDatabaseConfig(
    String url,
    String username,
    String password,
    String poolName,
    long connectionTimeout,
    int acquireRetry,
    long idleTimeout,
    long maxLifetime,
    int maxPoolSize,
    int minIdle

) {

    public R2dbcDatabaseConfig(
        String url,
        String username,
        String password,
        String poolName,
        @Nullable Long connectionTimeout,
        @Nullable Integer acquireRetry,
        @Nullable Long idleTimeout,
        @Nullable Long maxLifetime,
        @Nullable Integer maxPoolSize,
        @Nullable Integer minPoolSize) {
        this(
            url,
            username,
            password,
            poolName,
            connectionTimeout != null ? connectionTimeout : 30000L,
            acquireRetry != null ? acquireRetry : 1,
            idleTimeout != null ? idleTimeout : 10 * 60 * 1000L,
            maxLifetime != null ? maxLifetime : 0L,
            maxPoolSize != null ? maxPoolSize : 10,
            minPoolSize != null ? minPoolSize : 0
        );
    }
}
