package ru.tinkoff.kora.database.cassandra;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

public interface CassandraDatabaseModule extends CassandraModule {
    default CassandraConfig cassandraConfig(Config config, ConfigValueExtractor<CassandraConfig> extractor) {
        var value = config.get("cassandra");
        return extractor.extract(value);
    }

    default CassandraDatabase cassandraDatabase(CassandraConfig config, DataBaseTelemetryFactory telemetryFactory, @Nullable CassandraConfigurer configurer, @Nullable MeterRegistry meterRegistry) {
        return new CassandraDatabase(config, configurer, telemetryFactory, meterRegistry);
    }
}
