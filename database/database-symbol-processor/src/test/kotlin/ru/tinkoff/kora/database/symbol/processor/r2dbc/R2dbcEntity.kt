package ru.tinkoff.kora.database.symbol.processor.r2dbc

import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import ru.tinkoff.kora.database.r2dbc.mapper.parameter.R2dbcParameterColumnMapper
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultColumnMapper
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class AllNativeTypesEntity(
    val booleanPrimitive: Boolean,
    val booleanBoxed: Boolean?,
    val integerPrimitive: Int,
    val integerBoxed: Int?,
    val longPrimitive: Long,
    val longBoxed: Long?,
    val doublePrimitive: Double,
    val doubleBoxed: Double?,
    val string: String?,
    val bigDecimal: BigDecimal?,
    val byteArray: ByteArray?,
    val localDateTime: LocalDateTime?,
    val localDate: LocalDate?
)


class TestEntityFieldR2dbcResultColumnMapper : R2dbcResultColumnMapper<TestEntity.MappedField1> {
    override fun apply(row: Row, label: String): TestEntity.MappedField1 {
        return TestEntity.MappedField1()
    }
}

open class TestEntityFieldR2dbcResultColumnMapperNonFinal : R2dbcResultColumnMapper<TestEntity.MappedField2> {
    override fun apply(row: Row, label: String): TestEntity.MappedField2 {
        return TestEntity.MappedField2()
    }
}

class TestEntityR2dbcRowMapper : R2dbcRowMapper<TestEntity> {
    override fun apply(row: Row): TestEntity {
        return null!!
    }
}

open class TestEntityR2dbcRowMapperNonFinal : R2dbcRowMapper<TestEntity> {
    override fun apply(row: Row): TestEntity {
        return null!!
    }
}

class TestEntityFieldR2dbcParameterColumnMapper : R2dbcParameterColumnMapper<TestEntity.MappedField1?> {
    override fun apply(stmt: Statement, index: Int, o: TestEntity.MappedField1?) {}
}

open class TestEntityFieldR2dbcParameterColumnMapperNonFinal : R2dbcParameterColumnMapper<TestEntity.MappedField2?> {
    override fun apply(stmt: Statement, index: Int, o: TestEntity.MappedField2?) {}
}
