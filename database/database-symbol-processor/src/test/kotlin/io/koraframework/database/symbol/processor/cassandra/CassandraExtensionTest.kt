package io.koraframework.database.symbol.processor.cassandra

import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import org.junit.jupiter.api.Test

class CassandraExtensionTest : AbstractCassandraRepositoryTest() {
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
    fun testEntitySingleResultSetMapper() {
        compile0(
            listOf(KoraAppProcessorProvider(), CassandraEntitySymbolProcessorProvider()),
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
}
