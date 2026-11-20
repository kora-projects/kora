package io.koraframework.database.mongo;

import com.mongodb.MongoClientSettings;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import org.jspecify.annotations.Nullable;

public class MongoDatabaseFactoryModule {

    private final String configPath;

    public MongoDatabaseFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public MongoConfig mongoConfig(Config config, ConfigValueMapper<MongoConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public MongoDataSource mongoDataSource(@Tag(Tag.Factory.class) MongoConfig config,
                                           DatabaseTelemetryFactory telemetryFactory,
                                           @Tag(Tag.Factory.class) @Nullable Configurer<MongoClientSettings.Builder> configurer) {
        return new MongoDataSource(config, telemetryFactory, configurer);
    }
}
