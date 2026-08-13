package io.koraframework.database.symbol.processor

import io.koraframework.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MethodModifiersRepositoryTest : AbstractJdbcRepositoryTest() {
    @Test
    fun testSuspendFunIsRejected() {
        val result = compile0(
            listOf(RepositorySymbolProcessorProvider()),
            """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT 1")
                suspend fun test(): Int
            }
            """.trimIndent()
        ).assertFailure()

        assertThat(result.messages).anySatisfy {
            assertThat(it)
                .contains("Suspend methods are not supported by the repository generator")
                .contains("--enable-preview")
                .contains("StructuredTaskScope.open")
                .contains("Remove suspend from the method")
        }
    }

    @Test
    fun testInterfacePublicFun() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                fun test()
            }
            
            """.trimIndent())
    }

    @Test
    fun testAbstractClassPublicFun() {
        val repository = compile(listOf<Any>(), """
            @Repository
            abstract class TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                abstract fun test()
            }
            
            """.trimIndent())
    }

    @Test
    fun testAbstractClassProtectedFun() {
        val repository = compile(listOf<Any>(), """
            @Repository
            abstract class TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                protected abstract fun test()
            }
            
            """.trimIndent())
    }

}
