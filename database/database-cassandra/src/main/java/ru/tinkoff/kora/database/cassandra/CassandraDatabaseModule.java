package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.util.Configurer;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

public interface CassandraDatabaseModule extends CassandraModule {
    default CassandraConfig cassandraConfig(Config config, ConfigValueExtractor<CassandraConfig> extractor) {
        var value = config.get("cassandra");
        return extractor.extract(value);
    }

    default CassandraDatabase cassandraDatabase(CassandraConfig config, DataBaseTelemetryFactory telemetryFactory, @Nullable Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer, @Nullable Configurer<CqlSessionBuilder> sessionBuilderConfigurer) {
        return new CassandraDatabase(config, loaderConfigurer, sessionBuilderConfigurer, telemetryFactory);
    }
}
