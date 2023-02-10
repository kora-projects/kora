package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR

object CassandraTypes {
    val connection = ClassName("com.datastax.oss.driver.api.core", "CqlSession")
    val row = ClassName("com.datastax.oss.driver.api.core.cql", "Row")
    val reactiveResultSet = ClassName("com.datastax.dse.driver.api.core.cql.reactive", "ReactiveResultSet")
    val resultSet = ClassName("com.datastax.oss.driver.api.core.cql", "ResultSet")
    val batchStatement = ClassName("com.datastax.oss.driver.api.core.cql", "BatchStatement")
    val defaultBatchType = ClassName("com.datastax.oss.driver.api.core.cql", "DefaultBatchType")
    val statement = ClassName("com.datastax.oss.driver.api.core.cql", "Statement")
    val boundStatementBuilder = ClassName("com.datastax.oss.driver.api.core.cql", "BoundStatementBuilder")
    val connectionFactory = ClassName("ru.tinkoff.kora.database.cassandra", "CassandraConnectionFactory")
    val repository = ClassName("ru.tinkoff.kora.database.cassandra", "CassandraRepository")
    val cassandraProfileAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "CassandraProfile")
    val statementSetter = ClassName("ru.tinkoff.kora.database.cassandra.mapper.parameter", "CassandraStatementSetter")
    val parameterColumnMapper = ClassName("ru.tinkoff.kora.database.cassandra.mapper.parameter", "CassandraParameterColumnMapper")
    val rowMapper = ClassName("ru.tinkoff.kora.database.cassandra.mapper.result", "CassandraRowMapper")
    val rowColumnMapper = ClassName("ru.tinkoff.kora.database.cassandra.mapper.result", "CassandraRowColumnMapper")
    val reactiveResultSetMapper = ClassName("ru.tinkoff.kora.database.cassandra.mapper.result", "CassandraReactiveResultSetMapper")
    val resultSetMapper = ClassName("ru.tinkoff.kora.database.cassandra.mapper.result", "CassandraResultSetMapper")

    val udt = ClassName("ru.tinkoff.kora.database.cassandra", "UDT")
    val gettableByName = ClassName("com.datastax.oss.driver.api.core.data", "GettableByName")
    val settableByName = ClassName("com.datastax.oss.driver.api.core.data", "SettableByName")
        .parameterizedBy(STAR)
    val udtValue = ClassName("com.datastax.oss.driver.api.core.data", "UdtValue")
    val userDefinedType = ClassName("com.datastax.oss.driver.api.core.type", "UserDefinedType")
    val listType = ClassName("com.datastax.oss.driver.api.core.type", "ListType")
}
