package io.koraframework.database.jdbc.hikari;

import com.zaxxer.hikari.HikariConfig;
import io.koraframework.common.Configurer;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import io.koraframework.database.jdbc.JdbcDatabaseConfig;
import io.koraframework.database.jdbc.JdbcRepository;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Properties;

/**
 * <b>Русский</b>: Конфигурация описывающая Hikari соединения к Jdbc базе данных.
 * <hr>
 * <b>English</b>: Configuration describing Hikari connections to the Jdbc database.
 *
 * @see JdbcRepository
 */
@ConfigMapper
public interface HikariJdbcDatabaseConfig extends JdbcDatabaseConfig {

    default Duration connectionTimeout() {
        return Duration.ofSeconds(10);
    }

    default Duration idleTimeout() {
        return Duration.ofMinutes(10);
    }

    default Duration maxLifetime() {
        return Duration.ofMinutes(15);
    }

    default Duration leakDetectionThreshold() {
        return Duration.ofSeconds(0);
    }

    default int maxPoolSize() {
        return 10;
    }

    default int minIdle() {
        return 0;
    }

    default Properties dsProperties() {
        return new Properties();
    }

    static HikariConfig toHikariConfig(HikariJdbcDatabaseConfig config, @Nullable Configurer<HikariConfig> configurer) {
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
        if (configurer != null) {
            return configurer.configure(hikariConfig);
        }
        return hikariConfig;
    }
}
