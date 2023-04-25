package ru.tinkoff.kora.database.symbol.processor.r2dbc

import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultColumnMapper
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.EntityWithEmbedded
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
                typeOf<R2dbcRowMapper<EntityWithEmbedded>>(),
                typeOf<R2dbcResultFluxMapper<List<String>, Mono<List<String>>>>(),
                typeOf<R2dbcResultFluxMapper<String, Flux<String>>>(),
            ),
            typeOf<R2dbcResultColumnMapper<TestEntity.UnknownField>>(),
            typeOf<TestEntityFieldR2dbcResultColumnMapperNonFinal>(),
            typeOf<R2dbcRowMapper<String>>(),
        )
    }
}
