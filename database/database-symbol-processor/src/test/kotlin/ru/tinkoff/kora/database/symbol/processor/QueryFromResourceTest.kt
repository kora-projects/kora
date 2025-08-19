package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.tinkoff.kora.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest

class QueryFromResourceTest : AbstractJdbcRepositoryTest() {
    @KspExperimental
    @Test
    fun testNativeParameter() {
        val repository = compile(
            executor, listOf<Any>(), """
        @Repository
        interface TestRepository : JdbcRepository{
            @Query("classpath:/sql/test-query.sql")
            fun test()
        }
        """.trimIndent()
        )

        repository.invoke<Unit>("test")

        Mockito.verify(executor.mockConnection).prepareStatement("SELECT 1;\n")
    }
}
