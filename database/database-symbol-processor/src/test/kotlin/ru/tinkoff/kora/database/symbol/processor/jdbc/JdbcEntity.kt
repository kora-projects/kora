package ru.tinkoff.kora.database.symbol.processor.jdbc

import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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


class TestEntityFieldJdbcResultColumnMapper : JdbcResultColumnMapper<TestEntity.MappedField1?> {
    override fun apply(rs: ResultSet, index: Int): TestEntity.MappedField1? {
        return TestEntity.MappedField1()
    }
}

open class TestEntityFieldJdbcResultColumnMapperNonFinal : JdbcResultColumnMapper<TestEntity.MappedField2?> {
    override fun apply(rs: ResultSet, index: Int): TestEntity.MappedField2 {
        return TestEntity.MappedField2()
    }
}

class TestEntityJdbcRowMapper : JdbcRowMapper<TestEntity> {
    override fun apply(rs: ResultSet): TestEntity? {
        return null
    }
}

open class TestEntityJdbcRowMapperNonFinal : JdbcRowMapper<TestEntity> {
    override fun apply(rs: ResultSet): TestEntity {
        return null!!
    }
}

class OptionalMappedEntityResultSetMapper : JdbcResultSetMapper<Optional<TestEntity>> {
    override fun apply(rs: ResultSet): Optional<TestEntity> {
        return Optional.empty<TestEntity>()
    }
}

class ListMappedEntityResultSetMapper : JdbcResultSetMapper<List<TestEntity>> {
    override fun apply(rs: ResultSet): List<TestEntity> {
        return listOf<TestEntity>()
    }
}

class TestEntityFieldJdbcParameterColumnMapper : JdbcParameterColumnMapper<TestEntity.MappedField1?> {
    override fun set(stmt: PreparedStatement, index: Int, value: TestEntity.MappedField1?) {
    }
}

open class TestEntityFieldJdbcParameterColumnMapperNonFinal : JdbcParameterColumnMapper<TestEntity.MappedField2?> {
    override fun set(stmt: PreparedStatement, index: Int, value: TestEntity.MappedField2?) {
    }
}
