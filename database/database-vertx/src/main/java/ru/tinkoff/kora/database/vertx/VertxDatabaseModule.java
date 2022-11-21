package ru.tinkoff.kora.database.vertx;

import com.typesafe.config.Config;
import io.netty.channel.EventLoopGroup;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;
import ru.tinkoff.kora.netty.common.NettyCommonModule;
import ru.tinkoff.kora.vertx.common.VertxCommonModule;

public interface VertxDatabaseModule extends VertxDatabaseBaseModule {
    default VertxDatabaseConfig vertxDatabaseConfig(Config config, ConfigValueExtractor<VertxDatabaseConfig> extractor) {
        var value = config.getValue("db");
        return extractor.extract(value);
    }

    default VertxDatabase vertxDatabase(VertxDatabaseConfig vertxDatabaseConfig, EventLoopGroup eventLoopGroup, DataBaseTelemetryFactory telemetryFactory) {
        return new VertxDatabase(vertxDatabaseConfig, eventLoopGroup, telemetryFactory);
    }
}
