package io.koraframework.database.symbol.processor

import io.koraframework.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest
import org.junit.jupiter.api.Test

class NoQueryMethodsRepositoryTest : AbstractJdbcRepositoryTest() {
    @Test
    fun testCompiles() {
        compile(
            listOf<Any>(), """
        @Repository
        interface TestRepository : JdbcRepository
        """.trimIndent()
        )
    }
}
