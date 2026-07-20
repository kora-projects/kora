package io.koraframework.database.jdbc;

import io.koraframework.common.annotation.FactoryModule;

public interface JdbcDatabaseModule extends JdbcMapperModule {

    @FactoryModule
    default JdbcDatabaseFactoryModule jdbcDatabase() {
        return new JdbcDatabaseFactoryModule("jdbc");
    }
}
