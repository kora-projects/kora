package io.koraframework.database.symbol.processor.jdbc

import com.squareup.kotlinpoet.ClassName

object JdbcTypes {
    val connection = ClassName("java.sql", "Connection")
    val resultSet = ClassName("java.sql", "ResultSet")
    val jdbcEntity = ClassName("io.koraframework.database.jdbc.annotation", "EntityJdbc")
    val connectionFactory = ClassName("io.koraframework.database.jdbc", "JdbcExecutor")
    val jdbcDatabase = ClassName("io.koraframework.database.jdbc", "JdbcDataSource")
    val jdbcRepository = ClassName("io.koraframework.database.jdbc", "JdbcRepository")
    val jdbcResultSetMapper = ClassName("io.koraframework.database.jdbc.mapper.result", "JdbcResultSetMapper")
    val jdbcRowMapper = ClassName("io.koraframework.database.jdbc.mapper.result", "JdbcRowMapper")
    val jdbcResultColumnMapper = ClassName("io.koraframework.database.jdbc.mapper.result", "JdbcResultColumnMapper")

    val jdbcParameterColumnMapper = ClassName("io.koraframework.database.jdbc.mapper.parameter", "JdbcParameterColumnMapper")
}
