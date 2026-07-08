package io.koraframework.database.symbol.processor

import com.google.devtools.ksp.KspExperimental
import io.koraframework.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito

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

        Mockito.verify(executor.mockConnection).prepareStatement("SELECT 1;")
    }
}
