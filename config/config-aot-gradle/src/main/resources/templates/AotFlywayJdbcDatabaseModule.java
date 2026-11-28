package io.koraframework.config.aot.generated;

import io.koraframework.common.annotation.FactoryModule;

public interface AotFlywayJdbcDatabaseModule {

    @FactoryModule
    default AotFlywayFactoryModule aotFlywayFactoryModule() {
        return new AotFlywayFactoryModule("flyway");
    }
}
