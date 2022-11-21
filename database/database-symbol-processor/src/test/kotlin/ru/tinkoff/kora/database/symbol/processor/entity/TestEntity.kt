package ru.tinkoff.kora.database.symbol.processor.entity

import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.database.symbol.processor.cassandra.TestEntityFieldCassandraParameterColumnMapper
import ru.tinkoff.kora.database.symbol.processor.cassandra.TestEntityFieldCassandraParameterColumnMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.cassandra.TestEntityFieldCassandraResultColumnMapper
import ru.tinkoff.kora.database.symbol.processor.cassandra.TestEntityFieldCassandraResultColumnMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.jdbc.TestEntityFieldJdbcParameterColumnMapper
import ru.tinkoff.kora.database.symbol.processor.jdbc.TestEntityFieldJdbcParameterColumnMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.jdbc.TestEntityFieldJdbcResultColumnMapper
import ru.tinkoff.kora.database.symbol.processor.jdbc.TestEntityFieldJdbcResultColumnMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.r2dbc.TestEntityFieldR2dbcParameterColumnMapper
import ru.tinkoff.kora.database.symbol.processor.r2dbc.TestEntityFieldR2dbcParameterColumnMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.r2dbc.TestEntityFieldR2dbcResultColumnMapper
import ru.tinkoff.kora.database.symbol.processor.r2dbc.TestEntityFieldR2dbcResultColumnMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityFieldVertxParameterColumnMapper
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityFieldVertxParameterColumnMapperNonFinal
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityFieldVertxResultColumnMapper
import ru.tinkoff.kora.database.symbol.processor.vertx.TestEntityFieldVertxResultColumnMapperNonFinal

data class TestEntity(
    val field1: String,
    val field2: Int,
    val field3: Int?,
    val unknownTypeField: UnknownField,
    // mappers
    @Mapping(TestEntityFieldJdbcResultColumnMapper::class)
    @Mapping(TestEntityFieldJdbcParameterColumnMapper::class)
    @Mapping(TestEntityFieldCassandraResultColumnMapper::class)
    @Mapping(TestEntityFieldCassandraParameterColumnMapper::class)
    @Mapping(TestEntityFieldR2dbcResultColumnMapper::class)
    @Mapping(TestEntityFieldR2dbcParameterColumnMapper::class)
    @Mapping(TestEntityFieldVertxResultColumnMapper::class)
    @Mapping(TestEntityFieldVertxParameterColumnMapper::class)
    val mappedField1: MappedField1,
    // mappers
    @Mapping(TestEntityFieldJdbcResultColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldJdbcParameterColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldCassandraResultColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldCassandraParameterColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldR2dbcResultColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldR2dbcParameterColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldVertxResultColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldVertxParameterColumnMapperNonFinal::class)
    val mappedField2: MappedField2
) {
    class UnknownField
    class MappedField1
    class MappedField2
}
