package ru.tinkoff.kora.database.jdbc;

import com.typesafe.config.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

public interface JdbcDatabaseModule extends JdbcModule {
    default JdbcDataBaseConfig jdbcDataBaseConfig(Config config, ConfigValueExtractor<JdbcDataBaseConfig> extractor) {
        var value = config.getValue("db");
        return extractor.extract(value);
    }

    default JdbcDataBase jdbcDataBase(JdbcDataBaseConfig config, DataBaseTelemetryFactory telemetryFactory) {
        return new JdbcDataBase(config, telemetryFactory);
    }
}
