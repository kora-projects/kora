package ru.tinkoff.kora.database.jdbc;

import com.zaxxer.hikari.HikariConfig;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Properties;

@ConfigValueExtractor
public interface JdbcDatabaseConfig {
    String username();

    String password();

    String jdbcUrl();

    String poolName();

    @Nullable
    String schema();

    default Duration connectionTimeout() {
        return Duration.ofSeconds(10);
    }

    default Duration validationTimeout() {
        return Duration.ofSeconds(5);
    }

    default Duration idleTimeout() {
        return Duration.ofMinutes(60);
    }

    default Duration maxLifetime() {
        return Duration.ofMinutes(30);
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
