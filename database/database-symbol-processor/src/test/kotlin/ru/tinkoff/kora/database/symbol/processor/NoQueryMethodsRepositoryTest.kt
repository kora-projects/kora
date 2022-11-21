package ru.tinkoff.kora.database.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.symbol.processor.jdbc.MockJdbcExecutor
import ru.tinkoff.kora.database.symbol.processor.repository.NoQueryMethodsRepository

class NoQueryMethodsRepositoryTest {
    private val executor: MockJdbcExecutor = MockJdbcExecutor()
    private val repository: NoQueryMethodsRepository = DbTestUtils.compile(
        NoQueryMethodsRepository::class, executor
    )

    @Test
    fun testCompiles() {
        assertThat(repository).isNotNull
    }
}
