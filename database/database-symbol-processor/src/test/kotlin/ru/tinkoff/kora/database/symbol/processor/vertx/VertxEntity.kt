package ru.tinkoff.kora.database.symbol.processor.vertx

import io.vertx.core.buffer.Buffer
import io.vertx.sqlclient.Row
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.vertx.mapper.parameter.VertxParameterColumnMapper
import ru.tinkoff.kora.database.vertx.mapper.result.VertxResultColumnMapper
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper
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
    val byteArray: Buffer?,
    val localDateTime: LocalDateTime?,
    val localDate: LocalDate?
)


class TestEntityFieldVertxResultColumnMapper : VertxResultColumnMapper<TestEntity.MappedField1> {
    override fun apply(row: Row, index: Int): TestEntity.MappedField1 {
        return TestEntity.MappedField1()
    }
}

open class TestEntityFieldVertxResultColumnMapperNonFinal : VertxResultColumnMapper<TestEntity.MappedField2> {
    override fun apply(row: Row, index: Int): TestEntity.MappedField2 {
        return TestEntity.MappedField2()
    }
}

class TestEntityVertxRowMapper : VertxRowMapper<TestEntity?> {
    override fun apply(row: Row): TestEntity? {
        return null
    }
}
class TestEntityVertxRowMapperNonNull : VertxRowMapper<TestEntity> {
    override fun apply(row: Row): TestEntity? {
        return null
    }
}

open class TestEntityVertxRowMapperNonFinal : VertxRowMapper<TestEntity?> {
    override fun apply(row: Row): TestEntity? {
        return null
    }
}

class TestEntityFieldVertxParameterColumnMapper : VertxParameterColumnMapper<TestEntity.MappedField1?> {
    override fun apply(value: TestEntity.MappedField1?): Any {
        return TestEntity.MappedField1()
    }
}

class TestEntityFieldVertxParameterColumnMapperNonFinal : VertxParameterColumnMapper<TestEntity.MappedField2?> {
    override fun apply(value: TestEntity.MappedField2?): Any {
        return TestEntity.MappedField2()
    }
}
