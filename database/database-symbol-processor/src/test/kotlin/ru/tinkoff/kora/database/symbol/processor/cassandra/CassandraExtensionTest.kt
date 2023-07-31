package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.EntityWithEmbedded
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.TestUtils
import kotlin.reflect.typeOf

class CassandraExtensionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testEntity() {
        TestUtils.testKoraExtension(
            arrayOf(
                typeOf<CassandraRowMapper<TestEntity>>(),
                typeOf<CassandraRowMapper<AllNativeTypesEntity>>(),
                typeOf<CassandraResultSetMapper<List<AllNativeTypesEntity>>>(),
                typeOf<CassandraResultSetMapper<List<TestEntity>>>(),
                typeOf<CassandraRowMapper<EntityWithEmbedded>>(),
                typeOf<CassandraResultSetMapper<List<EntityWithEmbedded>>>(),
                typeOf<CassandraResultSetMapper<List<String>>>(),
            ),
            typeOf<CassandraRowColumnMapper<TestEntity.UnknownField>>(),
            typeOf<TestEntityFieldCassandraResultColumnMapperNonFinal>(),
            typeOf<CassandraRowMapper<String>>(),
        )
    }

    @Test
    fun testRowMapper() {
        compile(listOf(KoraAppProcessorProvider()), """
        @ru.tinkoff.kora.common.KoraApp
        interface TestApp : ru.tinkoff.kora.database.cassandra.CassandraModule {
            @ru.tinkoff.kora.common.annotation.Root
            fun root(m: ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper<TestEntity>) = ""
        }
        """.trimIndent(), """
        data class TestEntity(val value: String)
        """.trimIndent())
        compileResult.assertSuccess()
    }

    @Test
    fun testListResultSetMapper() {
        compile(listOf(KoraAppProcessorProvider()), """
        @ru.tinkoff.kora.common.KoraApp
        interface TestApp : ru.tinkoff.kora.database.cassandra.CassandraModule {
            @ru.tinkoff.kora.common.annotation.Root
            fun root(m: ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper<List<TestEntity>>) = ""
        }
        """.trimIndent(), """
        data class TestEntity(val value: String)
        """.trimIndent())
        compileResult.assertSuccess()
    }

    @Test
    fun testSingleResultSetMapper() {
        compile(listOf(KoraAppProcessorProvider()), """
        @ru.tinkoff.kora.common.KoraApp
        interface TestApp : ru.tinkoff.kora.database.cassandra.CassandraModule {
            @ru.tinkoff.kora.common.annotation.Root
            fun root(m: ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper<TestEntity>) = ""
        }
        """.trimIndent(), """
        data class TestEntity(val value: String)
        """.trimIndent())
        compileResult.assertSuccess()
    }
}
