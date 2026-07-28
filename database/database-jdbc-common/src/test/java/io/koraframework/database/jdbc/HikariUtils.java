package io.koraframework.database.jdbc;

import com.zaxxer.hikari.HikariConfig;

class HikariUtils {

    static HikariConfig toHikariConfig(JdbcDatabaseConfig config) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setValidationTimeout(config.validationTimeout().toMillis());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setPoolName(config.poolName());
        hikariConfig.setInitializationFailTimeout(-1);
        hikariConfig.setAutoCommit(true);
        hikariConfig.setSchema(config.schema());
        hikariConfig.setRegisterMbeans(false);
        return hikariConfig;
    }
}
