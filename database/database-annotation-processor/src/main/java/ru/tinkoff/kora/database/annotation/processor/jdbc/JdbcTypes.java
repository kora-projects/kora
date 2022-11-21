package ru.tinkoff.kora.database.annotation.processor.jdbc;

import com.squareup.javapoet.ClassName;

public class JdbcTypes {
    public static final ClassName CONNECTION = ClassName.get("java.sql", "Connection");
    public static final ClassName CONNECTION_FACTORY = ClassName.get("ru.tinkoff.kora.database.jdbc", "JdbcConnectionFactory");
    public static final ClassName JDBC_REPOSITORY = ClassName.get("ru.tinkoff.kora.database.jdbc", "JdbcRepository");

    public static final String RESULT_PACKAGE = "ru.tinkoff.kora.database.jdbc.mapper.result";
    public static final ClassName RESULT_SET_MAPPER = ClassName.get(RESULT_PACKAGE, "JdbcResultSetMapper");
    public static final ClassName ROW_MAPPER = ClassName.get(RESULT_PACKAGE, "JdbcRowMapper");
    public static final ClassName RESULT_COLUMN_MAPPER = ClassName.get(RESULT_PACKAGE, "JdbcResultColumnMapper");

    public static final String PARAMETER_PACKAGE = "ru.tinkoff.kora.database.jdbc.mapper.parameter";
    public static final ClassName PARAMETER_COLUMN_MAPPER = ClassName.get(PARAMETER_PACKAGE, "JdbcParameterColumnMapper");

}
