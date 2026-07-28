package io.koraframework.database.jdbc.hikari;

import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.database.jdbc.JdbcMapperModule;

public interface HikariJdbcDatabaseModule extends JdbcMapperModule {

    @FactoryModule
    default HikariJdbcDatabaseFactoryModule hikariJdbcDatabase() {
        return new HikariJdbcDatabaseFactoryModule("jdbc.hikari");
    }
}
