package io.koraframework.database.jdbc;

import io.koraframework.database.common.DatabaseModule;

public interface JdbcMapperModule extends
        DatabaseModule,
        PrimitiveJdbcMappersModule,
        TemporalJdbcMappersModule,
        CollectionJdbcResultSetMappersModule,
        SqlArrayJdbcColumnMappersModule,
        EnumJdbcMappersModule {
}
