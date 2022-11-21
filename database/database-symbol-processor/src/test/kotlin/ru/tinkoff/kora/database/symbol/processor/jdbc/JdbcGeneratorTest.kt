package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGeneratorTest {
    private val executor = MockJdbcExecutor()

    @BeforeEach
    internal fun setUp() {
        executor.reset()
    }

}
