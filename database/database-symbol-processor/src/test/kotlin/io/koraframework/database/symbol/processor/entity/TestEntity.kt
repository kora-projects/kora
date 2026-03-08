package io.koraframework.database.symbol.processor.entity

import io.koraframework.common.Mapping
import io.koraframework.database.symbol.processor.cassandra.TestEntityFieldCassandraParameterColumnMapper
import io.koraframework.database.symbol.processor.cassandra.TestEntityFieldCassandraParameterColumnMapperNonFinal
import io.koraframework.database.symbol.processor.cassandra.TestEntityFieldCassandraResultColumnMapper
import io.koraframework.database.symbol.processor.cassandra.TestEntityFieldCassandraResultColumnMapperNonFinal
import io.koraframework.database.symbol.processor.jdbc.TestEntityFieldJdbcParameterColumnMapper
import io.koraframework.database.symbol.processor.jdbc.TestEntityFieldJdbcParameterColumnMapperNonFinal
import io.koraframework.database.symbol.processor.jdbc.TestEntityFieldJdbcResultColumnMapper
import io.koraframework.database.symbol.processor.jdbc.TestEntityFieldJdbcResultColumnMapperNonFinal

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
    val mappedField1: MappedField1,
    // mappers
    @Mapping(TestEntityFieldJdbcResultColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldJdbcParameterColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldCassandraResultColumnMapperNonFinal::class)
    @Mapping(TestEntityFieldCassandraParameterColumnMapperNonFinal::class)
    val mappedField2: MappedField2
) {
    class UnknownField
    class MappedField1
    class MappedField2

    companion object {

        fun defaultData(): TestEntity {
            return TestEntity(
                "field1",
                42,
                43,
                UnknownField(),
                MappedField1(),
                MappedField2()
            )
        }
    }
}
