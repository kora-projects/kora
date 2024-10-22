package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.squareup.kotlinpoet.ClassName

object JdbcTypes {
    val connection = ClassName("java.sql", "Connection")
    val resultSet = ClassName("java.sql", "ResultSet")
    val jdbcEntity = ClassName("ru.tinkoff.kora.database.jdbc", "EntityJdbc")
    val connectionFactory = ClassName("ru.tinkoff.kora.database.jdbc", "JdbcConnectionFactory")
    val jdbcDatabase = ClassName("ru.tinkoff.kora.database.jdbc", "JdbcDatabase")
    val jdbcRepository = ClassName("ru.tinkoff.kora.database.jdbc", "JdbcRepository")
    val jdbcResultSetMapper = ClassName("ru.tinkoff.kora.database.jdbc.mapper.result", "JdbcResultSetMapper")
    val jdbcRowMapper = ClassName("ru.tinkoff.kora.database.jdbc.mapper.result", "JdbcRowMapper")
    val jdbcResultColumnMapper = ClassName("ru.tinkoff.kora.database.jdbc.mapper.result", "JdbcResultColumnMapper")

    val jdbcParameterColumnMapper = ClassName("ru.tinkoff.kora.database.jdbc.mapper.parameter", "JdbcParameterColumnMapper")
}
