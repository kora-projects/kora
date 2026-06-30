package io.koraframework.database.jdbc;

import com.zaxxer.hikari.HikariConfig;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;

public interface JdbcDatabaseModule extends JdbcMapperModule {

    default JdbcDatabaseConfig jdbcDatabaseConfig(Config config, ConfigValueMapper<JdbcDatabaseConfig> mapper) {
        return mapper.mapOrThrow(config.get("db.jdbc"));
    }

    default JdbcDataSource jdbcDatabase(JdbcDatabaseConfig config, DatabaseTelemetryFactory telemetryFactory, @Nullable Configurer<HikariConfig> configurer) {
        return new JdbcDataSource(config, telemetryFactory, configurer);
    }
}
