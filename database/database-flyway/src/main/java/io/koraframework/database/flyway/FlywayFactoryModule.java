package io.koraframework.database.flyway;

import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;

public class FlywayFactoryModule {

    private final String configPath;

    public FlywayFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public FlywayConfig flywayConfig(Config config, ConfigValueMapper<FlywayConfig> mapper) {
        return mapper.mapOrThrow(config.get(configPath));
    }

    @Tag(Tag.Factory.class)
    public FlywayJdbcDatabaseInterceptor flywayInterceptor(@Tag(Tag.Factory.class) FlywayConfig flywayConfig) {
        return new FlywayJdbcDatabaseInterceptor(flywayConfig);
    }
}
