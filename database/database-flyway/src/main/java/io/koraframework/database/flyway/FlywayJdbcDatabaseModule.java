package io.koraframework.database.flyway;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;

public interface FlywayJdbcDatabaseModule {

    default FlywayConfig flywayConfig(Config config, ConfigValueMapper<FlywayConfig> mapper) {
        return mapper.mapOrThrow(config.get("flyway"));
    }

    default FlywayJdbcDatabaseInterceptor flywayJdbcDatabaseInterceptor(FlywayConfig flywayConfig) {
        return new FlywayJdbcDatabaseInterceptor(flywayConfig);
    }
}
