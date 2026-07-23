package io.koraframework.database.liquibase;

import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;

public class LiquibaseFactoryModule {

    private final String configPath;

    public LiquibaseFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public LiquibaseConfig liquibaseConfig(Config config, ConfigValueMapper<LiquibaseConfig> mapper) {
        return mapper.mapOrThrow(config.get(configPath));
    }

    @Tag(Tag.Factory.class)
    public LiquibaseJdbcDatabaseInterceptor liquibaseJdbcDatabaseInterceptor(@Tag(Tag.Factory.class) LiquibaseConfig liquibaseConfig) {
        return new LiquibaseJdbcDatabaseInterceptor(liquibaseConfig);
    }
}
