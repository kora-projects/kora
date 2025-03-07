package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.EntityWithEmbedded
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.TestUtils
import kotlin.reflect.typeOf

class CassandraExtensionTest : AbstractCassandraRepositoryTest() {

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
        compile0(
            listOf(KoraAppProcessorProvider()),
            """
            @KoraApp
            interface TestApp : CassandraModule {
                @Root
                fun root(m: CassandraRowMapper<TestEntity>) = ""
            }
            """.trimIndent(),
            """
            data class TestEntity(val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testEntityRowMapper() {
        compile0(
            listOf(KoraAppProcessorProvider(), CassandraEntitySymbolProcessorProvider()),
            """
            @KoraApp
            interface TestApp : CassandraModule {
                @Root
                fun root(m: CassandraRowMapper<TestEntity>) = ""
            }
            """.trimIndent(),
            """
            @EntityCassandra
            data class TestEntity(val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testListResultSetMapper() {
        compile0(
            listOf(KoraAppProcessorProvider()),
            """
            @KoraApp
            interface TestApp : CassandraModule {
                @Root
                fun root(m: CassandraResultSetMapper<List<TestEntity>>) = ""
            }
            """.trimIndent(),
            """
            data class TestEntity(val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testEntityListResultSetMapper() {
        compile0(
            listOf(KoraAppProcessorProvider(), CassandraEntitySymbolProcessorProvider()),
            """
            @KoraApp
            interface TestApp : CassandraModule {
                @Root
                fun root(m: CassandraResultSetMapper<List<TestEntity>>) = ""
            }
            """.trimIndent(),
            """
            @EntityCassandra
            data class TestEntity(val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testSingleResultSetMapper() {
        compile0(
            listOf(KoraAppProcessorProvider()),
            """
            @KoraApp
            interface TestApp : CassandraModule {
                @Root
                fun root(m: CassandraResultSetMapper<TestEntity>) = ""
            }
            """.trimIndent(),
            """
            data class TestEntity(val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testEntitySingleResultSetMapper() {
        compile0(
            listOf(KoraAppProcessorProvider()),
            """
            @KoraApp
            interface TestApp : CassandraModule {
                @Root
                fun root(m: CassandraResultSetMapper<TestEntity>) = ""
            }
            """.trimIndent(),
            """
            @EntityCassandra
            data class TestEntity(val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testListAsyncResultSetMapper() {
        compile0(
            listOf(KoraAppProcessorProvider()),
            """
            @KoraApp
            interface TestApp : CassandraModule {
                @Root
                fun root(m: CassandraAsyncResultSetMapper<List<TestEntity>>) = ""
            }
            """.trimIndent(),
            """
            data class TestEntity(val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testSingleAsyncResultSetMapper() {
        compile0(
            listOf(KoraAppProcessorProvider()),
            """
            @KoraApp
            interface TestApp : CassandraModule {
                @Root
                fun root(m: CassandraAsyncResultSetMapper<TestEntity>) = ""
            }
            """.trimIndent(),
            """
            data class TestEntity(val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }
}
