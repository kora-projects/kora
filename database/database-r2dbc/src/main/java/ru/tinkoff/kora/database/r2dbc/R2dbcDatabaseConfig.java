package ru.tinkoff.kora.database.r2dbc;

import io.r2dbc.pool.ConnectionPoolConfiguration;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

public record R2dbcDatabaseConfig(
    String r2dbcUrl,
    String username,
    String password,
    String poolName,
    Duration connectionTimeout,
    Duration connectionCreateTimeout,
    Duration statementTimeout,
    Duration idleTimeout,
    Duration maxLifetime,
    int acquireRetry,
    int maxPoolSize,
    int minIdle,
    Map<String, String> options) {

    public R2dbcDatabaseConfig(
        String r2dbcUrl,
        String username,
        String password,
        String poolName,
        @Nullable Duration connectionTimeout,
        @Nullable Duration connectionCreateTimeout,
        @Nullable Duration statementTimeout,
        @Nullable Duration idleTimeout,
        @Nullable Duration maxLifetime,
        @Nullable Integer acquireRetry,
        @Nullable Integer maxPoolSize,
        @Nullable Integer minPoolSize,
        @Nullable Map<String, String> options) {
        this(
            r2dbcUrl,
            username,
            password,
            poolName,
            connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(30),
            connectionCreateTimeout != null ? connectionCreateTimeout : Duration.ofSeconds(30),
            statementTimeout,
            idleTimeout != null ? idleTimeout : Duration.ofMinutes(10),
            maxLifetime != null ? maxLifetime : ConnectionPoolConfiguration.NO_TIMEOUT,
            acquireRetry != null ? acquireRetry : 3,
            maxPoolSize != null ? maxPoolSize : 10,
            minPoolSize != null ? minPoolSize : 0,
            options != null ? options : Collections.emptyMap()
        );
    }
}
