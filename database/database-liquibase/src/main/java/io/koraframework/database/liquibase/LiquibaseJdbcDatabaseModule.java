package io.koraframework.database.liquibase;

import io.koraframework.common.annotation.FactoryModule;

public interface LiquibaseJdbcDatabaseModule {

    @FactoryModule
    default LiquibaseFactoryModule liquibaseFactoryModule() {
        return new LiquibaseFactoryModule("liquibase");
    }
}
