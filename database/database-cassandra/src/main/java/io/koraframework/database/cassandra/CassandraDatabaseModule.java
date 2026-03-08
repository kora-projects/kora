package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import io.koraframework.common.util.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.database.common.telemetry.DataBaseTelemetryFactory;
import org.jspecify.annotations.Nullable;

public interface CassandraDatabaseModule extends CassandraModule {
    default CassandraConfig cassandraConfig(Config config, ConfigValueExtractor<CassandraConfig> extractor) {
        var value = config.get("cassandra");
        return extractor.extract(value);
    }

    default CassandraDatabase cassandraDatabase(CassandraConfig config, DataBaseTelemetryFactory telemetryFactory, @Nullable Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer, @Nullable Configurer<CqlSessionBuilder> sessionBuilderConfigurer) {
        return new CassandraDatabase(config, loaderConfigurer, sessionBuilderConfigurer, telemetryFactory);
    }
}
