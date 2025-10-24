package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.KotlinCompilation

class CassandraExtensionTest : AbstractCassandraRepositoryTest() {
    @Test
    fun testEntityRowMapper() {
        KotlinCompilation().withClasspathJar("java-driver-core").compile(
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
        KotlinCompilation().withClasspathJar("java-driver-core").compile(
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
        KotlinCompilation().withClasspathJar("java-driver-core").compile(
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
