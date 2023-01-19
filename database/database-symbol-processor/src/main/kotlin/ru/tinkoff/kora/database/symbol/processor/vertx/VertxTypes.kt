package ru.tinkoff.kora.database.symbol.processor.vertx

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

object VertxTypes {
    val sqlConnection = ClassName("io.vertx.sqlclient", "SqlConnection")
    val sqlClient = ClassName("io.vertx.sqlclient", "SqlClient")
    val row = ClassName("io.vertx.sqlclient", "Row")
    val tuple = ClassName("io.vertx.sqlclient", "Tuple")
    val arrayTuple = ClassName("io.vertx.sqlclient.impl", "ArrayTuple")
    val rowSet = ClassName("io.vertx.sqlclient", "RowSet").parameterizedBy(row)
    val connectionFactory = ClassName("ru.tinkoff.kora.database.vertx", "VertxConnectionFactory")
    val repository = ClassName("ru.tinkoff.kora.database.vertx", "VertxRepository")
    val repositoryHelper = ClassName("ru.tinkoff.kora.database.vertx", "VertxRepositoryHelper")
    val rowSetMapper = ClassName("ru.tinkoff.kora.database.vertx.mapper.result", "VertxRowSetMapper")
    val rowMapper = ClassName("ru.tinkoff.kora.database.vertx.mapper.result", "VertxRowMapper")
    val resultColumnMapper = ClassName("ru.tinkoff.kora.database.vertx.mapper.result", "VertxResultColumnMapper")
    val parameterColumnMapper = ClassName("ru.tinkoff.kora.database.vertx.mapper.parameter", "VertxParameterColumnMapper")
}
