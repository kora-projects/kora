package ru.tinkoff.kora.database.jdbc;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import java.util.concurrent.Executor;

public interface JdbcDatabaseModule extends JdbcModule {

    default JdbcDatabaseConfig jdbcDataBaseConfig(Config config, ConfigValueExtractor<JdbcDatabaseConfig> extractor) {
        var value = config.get("db");
        return extractor.extract(value);
    }

    default JdbcDatabase jdbcDataBase(JdbcDatabaseConfig config,
                                      DataBaseTelemetryFactory telemetryFactory,
                                      @Tag(JdbcDatabase.class) @Nullable Executor executor) {
        return new JdbcDatabase(config, telemetryFactory, executor);
    }
}
