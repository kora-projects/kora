package ru.tinkoff.kora.database.jdbc;

import com.zaxxer.hikari.HikariConfig;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;
import java.time.Duration;
import java.util.Properties;

/**
 * <b>Русский</b>: Конфигурация описывающая соединения к Jdbc базе данных.
 * <hr>
 * <b>English</b>: Configuration describing connections to the Jdbc database.
 *
 * @see JdbcRepository
 */
@ConfigValueExtractor
public interface JdbcDatabaseConfig {
    /**
     * @return Username for the database connection.
     */
    String username();

    /**
     * @return User password for the database connection.
     */
    String password();

    /**
     * @return JDBC URL for connecting to the database.
     */
    String jdbcUrl();

    /**
     * @return Hikari connection pool name.
     */
    String poolName();

    /**
     * @return Database schema for the connection.
     */
    @Nullable
    String schema();

    /**
     * @return Maximum time to wait for a connection from the Hikari pool.
     */
    default Duration connectionTimeout() {
        return Duration.ofSeconds(10);
    }

    /**
     * @return Maximum time for Hikari connection validation.
     */
    default Duration validationTimeout() {
        return Duration.ofSeconds(5);
    }

    /**
     * @return Maximum idle time for a Hikari connection.
     */
    default Duration idleTimeout() {
        return Duration.ofMinutes(10);
    }

    /**
     * @return Maximum lifetime of a Hikari connection.
     */
    default Duration maxLifetime() {
        return Duration.ofMinutes(15);
    }

    /**
     * @return Time after which a busy connection is considered a possible leak.
     */
    default Duration leakDetectionThreshold() {
        return Duration.ofSeconds(0);
    }

    /**
     * @return Maximum Hikari connection pool size.
     */
    default int maxPoolSize() {
        return 10;
    }

    /**
     * @return Minimum number of idle ready connections in the Hikari pool.
     */
    default int minIdle() {
        return 0;
    }

    /**
     * @return Maximum time to wait for connection initialization at service startup.
     */
    @Nullable
    Duration initializationFailTimeout();

    /**
     * @return Whether to enable the readiness probe for the database connection.
     */
    default boolean readinessProbe() {
        return false;
    }

    /**
     * @return Additional JDBC connection properties passed to Hikari dataSourceProperties.
     */
    default Properties dsProperties() {
        return new Properties();
    }

    /**
     * @return Telemetry configuration for logging, metrics and tracing of database queries.
     */
    TelemetryConfig telemetry();

    static HikariConfig toHikariConfig(JdbcDatabaseConfig config) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setConnectionTimeout(config.connectionTimeout().toMillis());
        hikariConfig.setValidationTimeout(config.validationTimeout().toMillis());
        hikariConfig.setIdleTimeout(config.idleTimeout().toMillis());
        hikariConfig.setMaxLifetime(config.maxLifetime().toMillis());
        hikariConfig.setLeakDetectionThreshold(config.leakDetectionThreshold().toMillis());
        hikariConfig.setMaximumPoolSize(config.maxPoolSize());
        hikariConfig.setMinimumIdle(config.minIdle());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setPoolName(config.poolName());
        hikariConfig.setInitializationFailTimeout(-1);
        hikariConfig.setAutoCommit(true);
        hikariConfig.setSchema(config.schema());
        hikariConfig.setDataSourceProperties(config.dsProperties());
        hikariConfig.setRegisterMbeans(false);
        return hikariConfig;
    }
}
