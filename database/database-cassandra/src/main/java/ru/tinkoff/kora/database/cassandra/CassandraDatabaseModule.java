package ru.tinkoff.kora.database.cassandra;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

public interface CassandraDatabaseModule extends CassandraModule {
    default CassandraConfig cassandraConfig(Config config, ConfigValueExtractor<CassandraConfig> extractor) {
        var value = config.get("cassandra");
        return extractor.extract(value);
    }

    default CassandraDatabase cassandraDatabase(CassandraConfig config, DataBaseTelemetryFactory telemetryFactory) {
        return new CassandraDatabase(config, telemetryFactory);
    }
}
