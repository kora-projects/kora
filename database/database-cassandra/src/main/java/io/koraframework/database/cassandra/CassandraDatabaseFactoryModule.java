package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import org.jspecify.annotations.Nullable;

public class CassandraDatabaseFactoryModule {

    private final String configPath;

    public CassandraDatabaseFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public CassandraConfig cassandraConfig(Config config, ConfigValueMapper<CassandraConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public CassandraSession cassandraSession(@Tag(Tag.Factory.class) CassandraConfig config,
                                             DatabaseTelemetryFactory telemetryFactory,
                                             @Tag(Tag.Factory.class) @Nullable Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer,
                                             @Tag(Tag.Factory.class) @Nullable Configurer<CqlSessionBuilder> sessionBuilderConfigurer) {
        return new CassandraSession(config, telemetryFactory, loaderConfigurer, sessionBuilderConfigurer);
    }
}
