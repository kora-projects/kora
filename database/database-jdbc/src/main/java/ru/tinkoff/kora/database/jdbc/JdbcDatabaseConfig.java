package ru.tinkoff.kora.database.jdbc;

import com.zaxxer.hikari.HikariConfig;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Properties;

public record JdbcDatabaseConfig(
    String username,
    String password,
    String jdbcUrl,
    String poolName,
    @Nullable
    String schema,
    Duration connectionTimeout,
    Duration validationTimeout,
    Duration idleTimeout,
    Duration maxLifetime,
    Duration leakDetectionThreshold,
    int maxPoolSize,
    int minIdle,
    Properties dsProperties
) {

    public JdbcDatabaseConfig(
        String username,
        String password,
        String jdbcUrl,
        String poolName,
        @Nullable String schema,
        @Nullable Duration connectionTimeout,
        @Nullable Duration validationTimeout,
        @Nullable Duration idleTimeout,
        @Nullable Duration maxLifetime,
        @Nullable Duration leakDetectionThreshold,
        @Nullable Integer maxPoolSize,
        @Nullable Integer minIdle,
        @Nullable Properties dsProperties) {
        this(username,
            password,
            jdbcUrl,
            poolName,
            schema,
            connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(30),
            validationTimeout != null ? validationTimeout : Duration.ofSeconds(5),
            idleTimeout != null ? idleTimeout : Duration.ofMinutes(10),
            maxLifetime != null ? maxLifetime : Duration.ofMinutes(30),
            leakDetectionThreshold != null ? leakDetectionThreshold : Duration.ZERO,
            maxPoolSize != null ? maxPoolSize : 10,
            minIdle != null ? minIdle : 0,
            dsProperties != null ? props(dsProperties) : new Properties()
        );
    }

    private static Properties props(Properties props) {
        var properties = new Properties();
        properties.putAll(props);
        return properties;
    }

    public JdbcDatabaseConfig {
        assert dsProperties != null;
    }

    public HikariConfig toHikariConfig() {
        var config = new HikariConfig();
        config.setConnectionTimeout(this.connectionTimeout.toMillis());
        config.setValidationTimeout(this.validationTimeout.toMillis());
        config.setIdleTimeout(this.idleTimeout.toMillis());
        config.setMaxLifetime(this.maxLifetime.toMillis());
        config.setLeakDetectionThreshold(this.leakDetectionThreshold.toMillis());
        config.setMaximumPoolSize(this.maxPoolSize);
        config.setMinimumIdle(this.minIdle);
        config.setUsername(this.username);
        config.setPassword(this.password);
        config.setJdbcUrl(this.jdbcUrl);
        config.setPoolName(this.poolName);
        config.setInitializationFailTimeout(-1);
        config.setAutoCommit(true);
        config.setSchema(this.schema);
        config.setDataSourceProperties(this.dsProperties);
        config.setRegisterMbeans(false);

        return config;
    }
}
