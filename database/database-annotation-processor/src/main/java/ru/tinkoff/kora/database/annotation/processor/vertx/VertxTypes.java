package ru.tinkoff.kora.database.annotation.processor.vertx;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;

public class VertxTypes {
    public static final ClassName CONNECTION = ClassName.get("io.vertx.sqlclient", "SqlConnection");
    public static final ClassName SQL_CLIENT = ClassName.get("io.vertx.sqlclient", "SqlClient");
    public static final ClassName ROW = ClassName.get("io.vertx.sqlclient", "Row");
    public static final ClassName TUPLE = ClassName.get("io.vertx.sqlclient", "Tuple");
    public static final ParameterizedTypeName ROW_SET = ParameterizedTypeName.get(
        ClassName.get("io.vertx.sqlclient", "RowSet"), ROW
    );
    public static final ClassName CONNECTION_FACTORY = ClassName.get("ru.tinkoff.kora.database.vertx", "VertxConnectionFactory");
    public static final ClassName REPOSITORY = ClassName.get("ru.tinkoff.kora.database.vertx", "VertxRepository");
    public static final ClassName REPOSITORY_HELPER = ClassName.get("ru.tinkoff.kora.database.vertx", "VertxRepositoryHelper");

    public static final ClassName ROW_SET_MAPPER = ClassName.get("ru.tinkoff.kora.database.vertx.mapper.result", "VertxRowSetMapper");
    public static final ClassName ROW_MAPPER = ClassName.get("ru.tinkoff.kora.database.vertx.mapper.result", "VertxRowMapper");
    public static final ClassName RESULT_COLUMN_MAPPER = ClassName.get("ru.tinkoff.kora.database.vertx.mapper.result", "VertxResultColumnMapper");
    public static final ClassName PARAMETER_COLUMN_MAPPER = ClassName.get("ru.tinkoff.kora.database.vertx.mapper.parameter", "VertxParameterColumnMapper");
}
