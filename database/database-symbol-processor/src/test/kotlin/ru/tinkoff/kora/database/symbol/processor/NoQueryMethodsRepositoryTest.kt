package ru.tinkoff.kora.database.symbol.processor

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest

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
