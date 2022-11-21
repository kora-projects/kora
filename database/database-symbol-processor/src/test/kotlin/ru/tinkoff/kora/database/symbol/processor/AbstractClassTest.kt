package ru.tinkoff.kora.database.symbol.processor

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.tinkoff.kora.database.symbol.processor.jdbc.MockJdbcExecutor
import ru.tinkoff.kora.database.symbol.processor.repository.AbstractClassRepository

class AbstractClassTest {
    private val executor: MockJdbcExecutor = MockJdbcExecutor()
    private val repository: AbstractClassRepository = DbTestUtils.compile(
        AbstractClassRepository::class, "str", executor
    )

    @Test
    fun testNativeParameter() {
        repository.abstractMethod("test")
        Mockito.verify(executor.preparedStatement).setString(1, "test")
    }
}
