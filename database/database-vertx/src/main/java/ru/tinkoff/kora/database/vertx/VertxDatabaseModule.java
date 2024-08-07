package ru.tinkoff.kora.database.vertx;

import io.netty.channel.EventLoopGroup;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;
import ru.tinkoff.kora.netty.common.NettyChannelFactory;

public interface VertxDatabaseModule extends VertxDatabaseBaseModule {

    default VertxDatabaseConfig vertxDatabaseConfig(Config config, ConfigValueExtractor<VertxDatabaseConfig> extractor) {
        var value = config.get("db");
        return extractor.extract(value);
    }

    default VertxDatabase vertxDatabase(VertxDatabaseConfig vertxDatabaseConfig,
                                        @Tag(WorkerLoopGroup.class) EventLoopGroup eventLoopGroup,
                                        NettyChannelFactory nettyChannelFactory,
                                        DataBaseTelemetryFactory telemetryFactory) {
        return new VertxDatabase(vertxDatabaseConfig, eventLoopGroup, nettyChannelFactory, telemetryFactory);
    }
}
