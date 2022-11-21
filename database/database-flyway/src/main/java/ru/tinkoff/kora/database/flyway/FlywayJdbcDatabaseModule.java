package ru.tinkoff.kora.database.flyway;

public interface FlywayJdbcDatabaseModule {
    default FlywayJdbcDatabaseInterceptor flywayJdbcDatabaseInterceptor() {
        return new FlywayJdbcDatabaseInterceptor();
    }
}
