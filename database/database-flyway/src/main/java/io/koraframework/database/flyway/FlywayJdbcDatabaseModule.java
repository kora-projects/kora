package io.koraframework.database.flyway;

import io.koraframework.common.annotation.FactoryModule;

public interface FlywayJdbcDatabaseModule {

    @FactoryModule
    default FlywayFactoryModule flywayFactoryModule() {
        return new FlywayFactoryModule("flyway");
    }
}
