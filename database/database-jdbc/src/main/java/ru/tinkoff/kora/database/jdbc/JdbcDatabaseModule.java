package ru.tinkoff.kora.database.jdbc;

import com.typesafe.config.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

public interface JdbcDatabaseModule extends JdbcModule {
    default JdbcDatabaseConfig jdbcDataBaseConfig(Config config, ConfigValueExtractor<JdbcDatabaseConfig> extractor) {
        var value = config.getValue("db");
        return extractor.extract(value);
    }

    default JdbcDatabase jdbcDataBase(JdbcDatabaseConfig config, DataBaseTelemetryFactory telemetryFactory) {
        return new JdbcDatabase(config, telemetryFactory);
    }
}
