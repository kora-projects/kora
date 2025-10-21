package ru.tinkoff.kora.database.jdbc;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import java.util.Objects;

public interface JdbcDatabaseModule extends JdbcModule {

    default JdbcDatabaseConfig jdbcDataBaseConfig(Config config, ConfigValueExtractor<JdbcDatabaseConfig> extractor) {
        var value = config.get("db");
        return extractor.extract(value);
    }

    default JdbcDatabase jdbcDataBase(JdbcDatabaseConfig config,
                                      DataBaseTelemetryFactory telemetryFactory,
                                      @Nullable MeterRegistry meterRegistry) {
        return new JdbcDatabase(config, telemetryFactory, Objects.requireNonNullElse(meterRegistry, new CompositeMeterRegistry()));
    }
}
