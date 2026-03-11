package io.koraframework.database.symbol.processor

import io.koraframework.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AbstractClassTest : AbstractJdbcRepositoryTest() {

    @Test
    fun testAbstractClassRepository() {
        compile0(
            listOf(RepositorySymbolProcessorProvider()), """
            @Repository
            abstract class AbstractClassRepository(private val field: String?) : JdbcRepository {
                @Query("INSERT INTO table(value) VALUES (:value)")
                abstract fun abstractMethod(value: String?)
                fun nonAbstractMethod() {}
            }
        """.trimIndent()
        )

        compileResult.assertSuccess()
        assertThat(loadClass("\$AbstractClassRepository_Impl")).isFinal
    }

    @Test
    fun testAbstractClassRepositoryExtension() {
        compile0(
            listOf(RepositorySymbolProcessorProvider()), """
            @Repository
            abstract class AbstractClassRepository : JdbcRepository {
                @Query("INSERT INTO table(value) VALUES (:value)")
                abstract fun abstractMethod(value: String?)
            }
        """.trimIndent(), """
                    @KoraApp
                    interface TestApp {
                        fun factory() : io.koraframework.database.jdbc.JdbcConnectionFactory {
                          return org.mockito.Mockito.mock(io.koraframework.database.jdbc.JdbcConnectionFactory::class.java)
                        }
                        
                        @Root
                        fun root(repository: AbstractClassRepository) : String = "test"
                    }
                """.trimIndent()
        )

        compileResult.assertSuccess()
        assertThat(loadClass("\$AbstractClassRepository_Impl")).isFinal
    }
}
