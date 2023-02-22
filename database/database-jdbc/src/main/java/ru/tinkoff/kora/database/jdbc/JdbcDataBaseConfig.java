package ru.tinkoff.kora.database.jdbc;

import com.zaxxer.hikari.HikariConfig;

import javax.annotation.Nullable;
import java.util.Properties;

public record JdbcDataBaseConfig(
    String username,
    String password,
    String jdbcUrl,
    String poolName,
    @Nullable
    String schema,
    long connectionTimeout,
    long validationTimeout,
    long idleTimeout,
    long leakDetectionThreshold,
    long maxLifetime,
    int maxPoolSize,
    int minIdle,
    Properties dsProperties
) {

    public JdbcDataBaseConfig(
        String username,
        String password,
        String jdbcUrl,
        String poolName,
        @Nullable String schema,
        @Nullable Long connectionTimeout,
        @Nullable Long validationTimeout,
        @Nullable Long idleTimeout,
        @Nullable Long leakDetectionThreshold,
        @Nullable Long maxLifetime,
        @Nullable Integer maxPoolSize,
        @Nullable Integer minIdle,
        @Nullable Properties dsProperties) {
        this(username,
            password,
            jdbcUrl,
            poolName,
            schema,
            connectionTimeout != null ? connectionTimeout : 30000,
            validationTimeout != null ? validationTimeout : 5000,
            idleTimeout != null ? idleTimeout : 10 * 60 * 1000,
            leakDetectionThreshold != null ? leakDetectionThreshold : 0,
            maxLifetime != null ? maxLifetime : 30 * 60 * 1000,
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

    public JdbcDataBaseConfig {
        assert dsProperties != null;
    }

    public HikariConfig toHikariConfig() {
        var config = new HikariConfig();
        config.setConnectionTimeout(this.connectionTimeout);
        config.setValidationTimeout(this.validationTimeout);
        config.setIdleTimeout(this.idleTimeout);
        config.setLeakDetectionThreshold(this.leakDetectionThreshold);
        config.setMaxLifetime(this.maxLifetime);
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
