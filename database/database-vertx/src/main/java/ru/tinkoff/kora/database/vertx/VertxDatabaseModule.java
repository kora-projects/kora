package ru.tinkoff.kora.database.vertx;

import io.netty.channel.EventLoopGroup;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

public interface VertxDatabaseModule extends VertxDatabaseBaseModule {
    default VertxDatabaseConfig vertxDatabaseConfig(Config config, ConfigValueExtractor<VertxDatabaseConfig> extractor) {
        var value = config.get("db");
        return extractor.extract(value);
    }

    default VertxDatabase vertxDatabase(VertxDatabaseConfig vertxDatabaseConfig, EventLoopGroup eventLoopGroup, DataBaseTelemetryFactory telemetryFactory) {
        return new VertxDatabase(vertxDatabaseConfig, eventLoopGroup, telemetryFactory);
    }
}
