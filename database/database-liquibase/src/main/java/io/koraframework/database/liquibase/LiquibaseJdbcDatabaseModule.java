package io.koraframework.database.liquibase;

public interface LiquibaseJdbcDatabaseModule {

    default LiquibaseFactoryModule liquibaseFactoryModule() {
        return new LiquibaseFactoryModule("liquibase");
    }
}
