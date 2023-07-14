package ru.tinkoff.kora.database.symbol.processor.app

import org.mockito.Mockito
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory
import ru.tinkoff.kora.database.jdbc.JdbcRepository

@KoraApp
interface TestKoraApp {
    @Repository
    interface TestRepository : JdbcRepository {
        @Query("INSERT INTO table(value) VALUES (:value)")
        fun abstractMethod(value: String?)
    }

    fun jdbcQueryExecutorAccessor(): JdbcConnectionFactory {
        return Mockito.mock<JdbcConnectionFactory>(JdbcConnectionFactory::class.java)
    }

    @Root
    fun mockLifecycle(testRepository: TestRepository) = Any()
}
