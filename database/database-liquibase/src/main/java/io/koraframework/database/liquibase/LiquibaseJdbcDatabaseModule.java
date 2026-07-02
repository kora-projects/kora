package io.koraframework.database.liquibase;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;

public interface LiquibaseJdbcDatabaseModule {

    default LiquibaseConfig liquibaseConfig(Config config, ConfigValueMapper<LiquibaseConfig> mapper) {
        return mapper.mapOrThrow(config.get("liquibase"));
    }

    default LiquibaseJdbcDatabaseInterceptor liquibaseJdbcDatabaseInterceptor(LiquibaseConfig liquibaseConfig) {
        return new LiquibaseJdbcDatabaseInterceptor(liquibaseConfig);
    }
}
