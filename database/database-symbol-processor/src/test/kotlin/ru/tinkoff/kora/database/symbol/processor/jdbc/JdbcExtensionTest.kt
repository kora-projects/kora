package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.ksp.common.TestUtils
import kotlin.reflect.typeOf

class JdbcExtensionTest {
    @Test
    fun testEntity() {
        TestUtils.testKoraExtension(
            arrayOf(
                typeOf<JdbcRowMapper<TestEntity>>(),
                typeOf<JdbcRowMapper<AllNativeTypesEntity>>(),
                typeOf<JdbcResultSetMapper<AllNativeTypesEntity>>(),
                typeOf<JdbcResultSetMapper<TestEntity>>(),
                typeOf<JdbcResultSetMapper<List<AllNativeTypesEntity>>>(),
                typeOf<JdbcResultSetMapper<List<TestEntity>>>(),
            ),
            typeOf<JdbcResultColumnMapper<TestEntity.UnknownField>>(),
            typeOf<TestEntityFieldJdbcResultColumnMapperNonFinal>(),
        )
    }
}
