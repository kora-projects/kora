package ru.tinkoff.kora.database.liquibase;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

public interface LiquibaseJdbcDatabaseModule {

    default LiquibaseConfig liquibaseConfig(Config config, ConfigValueExtractor<LiquibaseConfig> extractor) {
        var value = config.get("liquibase");
        return extractor.extract(value);
    }

    default LiquibaseJdbcDatabaseInterceptor liquibaseJdbcDatabaseInterceptor(LiquibaseConfig liquibaseConfig) {
        return new LiquibaseJdbcDatabaseInterceptor(liquibaseConfig);
    }
}
