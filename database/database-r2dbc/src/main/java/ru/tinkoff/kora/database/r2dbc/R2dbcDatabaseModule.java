package ru.tinkoff.kora.database.r2dbc;

import com.typesafe.config.Config;
import io.r2dbc.spi.ConnectionFactoryOptions;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import java.util.function.Function;

public interface R2dbcDatabaseModule extends R2dbcModule {

    default R2dbcDatabaseConfig r2dbcConfig(Config config, ConfigValueExtractor<R2dbcDatabaseConfig> extractor) {
        var value = config.getValue("db");
        return extractor.extract(value);
    }

    default R2dbcDatabase r2dbcDatabase(R2dbcDatabaseConfig config, All<Function<ConnectionFactoryOptions.Builder, ConnectionFactoryOptions.Builder>> customizers, DataBaseTelemetryFactory telemetryFactory) {
        return new R2dbcDatabase(
            config,
            customizers,
            telemetryFactory
        );
    }
}
