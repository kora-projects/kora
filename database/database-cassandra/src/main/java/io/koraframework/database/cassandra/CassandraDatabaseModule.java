package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import io.koraframework.common.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import org.jspecify.annotations.Nullable;

public interface CassandraDatabaseModule extends CassandraMapperModule {

    default CassandraConfig cassandraConfig(Config config, ConfigValueExtractor<CassandraConfig> extractor) {
        return extractor.extractOrThrow(config.get("cassandra"));
    }

    default CassandraDataSource cassandraDatabase(CassandraConfig config, DatabaseTelemetryFactory telemetryFactory, @Nullable Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer, @Nullable Configurer<CqlSessionBuilder> sessionBuilderConfigurer) {
        return new CassandraDataSource(config, loaderConfigurer, sessionBuilderConfigurer, telemetryFactory);
    }
}
