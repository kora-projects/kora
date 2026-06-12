package io.koraframework.database.jdbc;

import com.zaxxer.hikari.HikariConfig;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.util.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;

public interface JdbcDatabaseModule extends JdbcModule {

    default JdbcDatabaseConfig jdbcDatabaseConfig(Config config, ConfigValueExtractor<JdbcDatabaseConfig> extractor) {
        var value = config.get("db");
        return extractor.extract(value);
    }

    default JdbcDatabase jdbcDatabase(JdbcDatabaseConfig config, DatabaseTelemetryFactory telemetryFactory, @Nullable Configurer<HikariConfig> configurer) {
        return new JdbcDatabase(config, telemetryFactory, configurer);
    }
}
