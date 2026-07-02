package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import io.koraframework.common.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import org.jspecify.annotations.Nullable;

public interface CassandraDatabaseModule extends CassandraModule {

    default CassandraConfig cassandraConfig(Config config, ConfigValueMapper<CassandraConfig> mapper) {
        return mapper.mapOrThrow(config.get("cassandra"));
    }

    default CassandraDatabase cassandraDatabase(CassandraConfig config, DatabaseTelemetryFactory telemetryFactory, @Nullable Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer, @Nullable Configurer<CqlSessionBuilder> sessionBuilderConfigurer) {
        return new CassandraDatabase(config, loaderConfigurer, sessionBuilderConfigurer, telemetryFactory);
    }
}
