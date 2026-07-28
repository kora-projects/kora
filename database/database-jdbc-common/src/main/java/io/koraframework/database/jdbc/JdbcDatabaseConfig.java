package io.koraframework.database.jdbc;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * <b>Русский</b>: Конфигурация описывающая соединения к Jdbc базе данных.
 * <hr>
 * <b>English</b>: Configuration describing connections to the Jdbc database.
 *
 * @see JdbcRepository
 */
@ConfigMapper
public interface JdbcDatabaseConfig {

    String username();

    String password();

    String jdbcUrl();

    String poolName();

    @Nullable
    String schema();

    default Duration validationTimeout() {
        return Duration.ofSeconds(5);
    }

    @Nullable
    Duration initializationFailTimeout();

    default boolean readinessProbe() {
        return false;
    }

    DatabaseTelemetryConfig telemetry();
}
