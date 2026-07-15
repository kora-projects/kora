package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.JdbcDatabaseModule;

public interface PostgresJdbcDatabaseModule extends
        JdbcDatabaseModule,
        PgIntervalJdbcMappersModule,
        PostgresArrayColumnDataModule,
        PostgresEnumJdbcMappersModule,
        PostgresRangeJdbcMappersModule {
}
