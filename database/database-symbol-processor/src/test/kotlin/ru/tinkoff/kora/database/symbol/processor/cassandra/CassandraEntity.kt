package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.data.GettableByName
import com.datastax.oss.driver.api.core.data.SettableByName
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
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
    val localDateTime: LocalDateTime?,
    val localDate: LocalDate?
)

class TestEntityFieldCassandraResultColumnMapper : CassandraRowColumnMapper<TestEntity.MappedField1> {
    override fun apply(row: GettableByName, column: Int): TestEntity.MappedField1 {
        return TestEntity.MappedField1()
    }
}

open class TestEntityFieldCassandraResultColumnMapperNonFinal : CassandraRowColumnMapper<TestEntity.MappedField2> {
    override fun apply(row: GettableByName, column: Int): TestEntity.MappedField2 {
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

class ListTestEntityCassandraResultSetMapper :
    CassandraReactiveResultSetMapper<List<TestEntity>, Mono<List<TestEntity>>> {
    override fun apply(rs: ReactiveResultSet): Mono<List<TestEntity>> {
        TODO()
    }
}
