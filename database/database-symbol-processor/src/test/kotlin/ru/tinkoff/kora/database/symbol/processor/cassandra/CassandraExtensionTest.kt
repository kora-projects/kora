package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.ksp.common.TestUtils
import kotlin.reflect.typeOf

class CassandraExtensionTest {
    @Test
    fun testEntity() {
        TestUtils.testKoraExtension(
            arrayOf(
                typeOf<CassandraRowMapper<TestEntity>>(),
                typeOf<CassandraRowMapper<AllNativeTypesEntity>>(),
                typeOf<CassandraResultSetMapper<List<AllNativeTypesEntity>>>(),
                typeOf<CassandraResultSetMapper<List<TestEntity>>>(),
            ),
            typeOf<CassandraRowColumnMapper<TestEntity.UnknownField?>>(),
            typeOf<TestEntityFieldCassandraResultColumnMapperNonFinal>(),
        )
    }
}
