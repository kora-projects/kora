package ru.tinkoff.kora.database.r2dbc;

import io.r2dbc.pool.ConnectionPoolConfiguration;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DatabaseTelemetryConfig;
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
    String r2dbcUrl();

    String username();

    String password();

    String poolName();

    default Duration connectionTimeout() {
        return Duration.ofSeconds(10);
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

    default boolean readinessProbe() {
        return false;
    }

    default Map<String, String> options() {
        return Map.of();
    }

    DatabaseTelemetryConfig telemetry();
}
