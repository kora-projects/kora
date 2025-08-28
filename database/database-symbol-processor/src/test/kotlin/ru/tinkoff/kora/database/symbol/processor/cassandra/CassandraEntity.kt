package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.data.GettableByName
import com.datastax.oss.driver.api.core.data.SettableByName
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class AllNativeTypesEntity(
    val booleanPrimitive: Boolean,
    val booleanBoxed: Boolean?,
    val shortPrimitive: Short,
    val shortBoxed: Short?,
    val integerPrimitive: Int,
    val integerBoxed: Int?,
    val longPrimitive: Long,
    val longBoxed: Long?,
    val doublePrimitive: Double,
    val doubleBoxed: Double?,
    val string: String?,
    val bigDecimal: BigDecimal?,
    val localDateTime: LocalDateTime?,
    val localDate: LocalDate?
)

class TestEntityFieldCassandraResultColumnMapper : CassandraRowColumnMapper<TestEntity.MappedField1> {
    override fun apply(row: GettableByName, column: Int): TestEntity.MappedField1 {
        return TestEntity.MappedField1()
    }
}

open class TestEntityFieldCassandraResultColumnMapperNonFinal : CassandraRowColumnMapper<TestEntity.MappedField2?> {
    override fun apply(row: GettableByName, column: Int): TestEntity.MappedField2? {
        return TestEntity.MappedField2()
    }
}

class TestEntityFieldCassandraParameterColumnMapper : CassandraParameterColumnMapper<TestEntity.MappedField1?> {
    override fun apply(stmt: SettableByName<*>, index: Int, value: TestEntity.MappedField1?) {}
}

open class TestEntityFieldCassandraParameterColumnMapperNonFinal : CassandraParameterColumnMapper<TestEntity.MappedField2?> {
    override fun apply(stmt: SettableByName<*>, index: Int, value: TestEntity.MappedField2?) {}
}

class TestEntityCassandraRowMapper : CassandraRowMapper<TestEntity> {
    override fun apply(row: Row): TestEntity {
        TODO()
    }
}

open class TestEntityCassandraRowMapperNonFinal : CassandraRowMapper<TestEntity> {
    override fun apply(row: Row): TestEntity {
        TODO()
    }
}
