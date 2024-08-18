package ru.tinkoff.kora.database.liquibase;

public interface LiquibaseJdbcDatabaseModule {
    default LiquibaseJdbcDatabaseInterceptor liquibaseJdbcDatabaseInterceptor(){return new LiquibaseJdbcDatabaseInterceptor();}
}
