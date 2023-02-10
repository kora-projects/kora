package ru.tinkoff.kora.database.symbol.processor.r2dbc

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultColumnMapper
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.ksp.common.TestUtils
import kotlin.reflect.typeOf

class R2dbcExtensionTest {
    @Test
    fun testEntity() {
        TestUtils.testKoraExtension(
            arrayOf(
                typeOf<R2dbcRowMapper<TestEntity>>(),
                typeOf<R2dbcRowMapper<AllNativeTypesEntity>>(),
            ),
            typeOf<R2dbcResultColumnMapper<TestEntity.UnknownField?>>(),
            typeOf<TestEntityFieldR2dbcResultColumnMapperNonFinal>(),
        )
    }
}
