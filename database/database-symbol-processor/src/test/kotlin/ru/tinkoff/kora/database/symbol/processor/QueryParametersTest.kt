package ru.tinkoff.kora.database.symbol.processor

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest


class QueryParametersTest : AbstractJdbcRepositoryTest() {
    @Test
    fun testDifferentPatterns() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("UPDATE TEST set v1 = :param1, v2 = fun(:param2), v3 = idx[:param3], v4 = :param4;")
                fun test(param1: String, param2: String, param3: String, param4: String)
            }
            
            """.trimIndent()
        )
    }
}
