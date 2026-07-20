package io.koraframework.database.jdbc;

import com.zaxxer.hikari.HikariConfig;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import org.jspecify.annotations.Nullable;

public class JdbcDatabaseFactoryModule {

    private final String configPath;

    public JdbcDatabaseFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public JdbcDatabaseConfig jdbcDatabaseConfig(Config config, ConfigValueMapper<JdbcDatabaseConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public JdbcDataSource jdbcDataSource(@Tag(Tag.Factory.class) JdbcDatabaseConfig config,
                                         DatabaseTelemetryFactory telemetryFactory,
                                         @Tag(Tag.Factory.class) @Nullable Configurer<HikariConfig> configurer) {
        return new JdbcDataSource(config, telemetryFactory, configurer);
    }
}
