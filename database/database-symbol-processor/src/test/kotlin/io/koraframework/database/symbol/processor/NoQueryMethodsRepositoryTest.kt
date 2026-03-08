package io.koraframework.database.symbol.processor

import org.junit.jupiter.api.Test
import io.koraframework.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest

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
