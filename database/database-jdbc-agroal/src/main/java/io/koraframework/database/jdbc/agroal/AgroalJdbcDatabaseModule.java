package io.koraframework.database.jdbc.agroal;

import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.database.jdbc.JdbcMapperModule;

public interface AgroalJdbcDatabaseModule extends JdbcMapperModule {

    @FactoryModule
    default AgroalJdbcDatabaseFactoryModule agroalJdbcDatabase() {
        return new AgroalJdbcDatabaseFactoryModule("jdbc.agroal");
    }
}
