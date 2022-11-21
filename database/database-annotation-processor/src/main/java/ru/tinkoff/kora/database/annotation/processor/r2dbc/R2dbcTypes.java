package ru.tinkoff.kora.database.annotation.processor.r2dbc;

import com.squareup.javapoet.ClassName;

public class R2dbcTypes {
    public static final ClassName CONNECTION = ClassName.get("io.r2dbc.spi", "Connection");
    public static final ClassName ROW = ClassName.get("io.r2dbc.spi", "Row");
    public static final ClassName RESULT = ClassName.get("io.r2dbc.spi", "Result");
    public static final ClassName R2DBC_REPOSITORY = ClassName.get("ru.tinkoff.kora.database.r2dbc", "R2dbcRepository");
    public static final ClassName CONNECTION_FACTORY = ClassName.get("ru.tinkoff.kora.database.r2dbc", "R2dbcConnectionFactory");

    public static final ClassName ROW_MAPPER = ClassName.get("ru.tinkoff.kora.database.r2dbc.mapper.result", "R2dbcRowMapper");
    public static final ClassName RESULT_FLUX_MAPPER = ClassName.get("ru.tinkoff.kora.database.r2dbc.mapper.result", "R2dbcResultFluxMapper");
    public static final ClassName RESULT_COLUMN_MAPPER = ClassName.get("ru.tinkoff.kora.database.r2dbc.mapper.result", "R2dbcResultColumnMapper");

    public static final ClassName PARAMETER_COLUMN_MAPPER = ClassName.get("ru.tinkoff.kora.database.r2dbc.mapper.parameter", "R2dbcParameterColumnMapper");
}
