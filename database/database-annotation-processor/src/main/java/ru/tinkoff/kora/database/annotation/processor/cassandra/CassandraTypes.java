package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.WildcardTypeName;

public class CassandraTypes {
    public static final ClassName CONNECTION = ClassName.get("com.datastax.oss.driver.api.core", "CqlSession");
    public static final ClassName ROW = ClassName.get("com.datastax.oss.driver.api.core.cql", "Row");
    public static final ClassName RESULT_SET = ClassName.get("com.datastax.oss.driver.api.core.cql", "ResultSet");
    public static final ClassName REACTIVE_RESULT_SET = ClassName.get("com.datastax.dse.driver.api.core.cql.reactive", "ReactiveResultSet");
    public static final ClassName BATCH_STATEMENT = ClassName.get("com.datastax.oss.driver.api.core.cql", "BatchStatement");
    public static final ClassName DEFAULT_BATCH_TYPE = ClassName.get("com.datastax.oss.driver.api.core.cql", "DefaultBatchType");
    public static final ClassName STATEMENT = ClassName.get("com.datastax.oss.driver.api.core.cql", "Statement");
    public static final ClassName BOUND_STATEMENT_BUILDER = ClassName.get("com.datastax.oss.driver.api.core.cql", "BoundStatementBuilder");


    public static final ClassName CONNECTION_FACTORY = ClassName.get("ru.tinkoff.kora.database.cassandra", "CassandraConnectionFactory");
    public static final ClassName REPOSITORY = ClassName.get("ru.tinkoff.kora.database.cassandra", "CassandraRepository");

    public static final ClassName CASSANDRA_PROFILE = ClassName.get("ru.tinkoff.kora.database.common.annotation", "CassandraProfile");
    public static final ClassName STATEMENT_SETTER = ClassName.get("ru.tinkoff.kora.database.cassandra.mapper.parameter", "CassandraStatementSetter");
    public static final ClassName PARAMETER_COLUMN_MAPPER = ClassName.get("ru.tinkoff.kora.database.cassandra.mapper.parameter", "CassandraParameterColumnMapper");
    public static final ClassName ROW_MAPPER = ClassName.get("ru.tinkoff.kora.database.cassandra.mapper.result", "CassandraRowMapper");
    public static final ClassName RESULT_COLUMN_MAPPER = ClassName.get("ru.tinkoff.kora.database.cassandra.mapper.result", "CassandraRowColumnMapper");
    public static final ClassName REACTIVE_RESULT_SET_MAPPER = ClassName.get("ru.tinkoff.kora.database.cassandra.mapper.result", "CassandraReactiveResultSetMapper");
    public static final ClassName RESULT_SET_MAPPER = ClassName.get("ru.tinkoff.kora.database.cassandra.mapper.result", "CassandraResultSetMapper");
    public static final ParameterizedTypeName SETTABLE_BY_NAME = ParameterizedTypeName.get(ClassName.get("com.datastax.oss.driver.api.core.data", "SettableByName"), WildcardTypeName.subtypeOf(TypeName.OBJECT));
    public static final ClassName GETTABLE_BY_NAME = ClassName.get("com.datastax.oss.driver.api.core.data", "GettableByName");

    public static final ClassName USER_DEFINED_TYPE = ClassName.get("com.datastax.oss.driver.api.core.type", "UserDefinedType");
    public static final ClassName UDT_ANNOTATION = ClassName.get("ru.tinkoff.kora.database.cassandra", "UDT");
}
