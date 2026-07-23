package io.koraframework.database.flyway;

public interface FlywayJdbcDatabaseModule {

    default FlywayFactoryModule flywayFactoryModule() {
        return new FlywayFactoryModule("flyway");
    }
}
