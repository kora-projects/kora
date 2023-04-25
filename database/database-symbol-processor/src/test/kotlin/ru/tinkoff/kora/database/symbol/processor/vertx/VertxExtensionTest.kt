package ru.tinkoff.kora.database.symbol.processor.vertx

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.symbol.processor.entity.EntityWithEmbedded
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.vertx.mapper.result.VertxResultColumnMapper
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper
import ru.tinkoff.kora.ksp.common.TestUtils
import kotlin.reflect.typeOf

class VertxExtensionTest {
    @Test
    fun testEntity() {
        TestUtils.testKoraExtension(
            arrayOf(
                typeOf<VertxRowMapper<TestEntity>>(),
                typeOf<VertxRowMapper<AllNativeTypesEntity>>(),
                typeOf<VertxRowSetMapper<List<TestEntity>>>(),
                typeOf<VertxRowSetMapper<List<AllNativeTypesEntity>>>(),
                typeOf<VertxRowSetMapper<TestEntity>>(),
                typeOf<VertxRowSetMapper<AllNativeTypesEntity>>(),
                typeOf<VertxRowMapper<EntityWithEmbedded>>(),
                typeOf<VertxRowSetMapper<List<EntityWithEmbedded>>>(),
                typeOf<VertxRowSetMapper<EntityWithEmbedded>>(),
                typeOf<VertxRowSetMapper<List<String>>>(),
            ),
            typeOf<VertxResultColumnMapper<TestEntity.UnknownField>>(),
            typeOf<TestEntityFieldVertxResultColumnMapperNonFinal>(),
            typeOf<VertxRowMapper<String>>(),
        )
    }
}
