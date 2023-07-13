package ru.tinkoff.kora.database.r2dbc;

import io.r2dbc.pool.ConnectionPoolConfiguration;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface R2dbcDatabaseConfig {
    String r2dbcUrl();

    String username();

    String password();

    String poolName();

    default Duration connectionTimeout() {
        return Duration.ofSeconds(30);
    }

    default Duration connectionCreateTimeout() {
        return Duration.ofSeconds(30);
    }

    @Nullable
    Duration statementTimeout();

    default Duration idleTimeout() {
        return Duration.ofMinutes(10);
    }

    default Duration maxLifetime() {
        return ConnectionPoolConfiguration.NO_TIMEOUT;
    }

    default int acquireRetry() {
        return 3;
    }

    default int maxPoolSize() {
        return 10;
    }

    default int minIdle() {
        return 0;
    }

    default Map<String, String> options() {
        return Map.of();
    }
}
