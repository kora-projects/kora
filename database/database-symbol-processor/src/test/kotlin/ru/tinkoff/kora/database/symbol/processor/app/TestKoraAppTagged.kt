package ru.tinkoff.kora.database.symbol.processor.app

import org.mockito.Mockito
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory
import java.util.concurrent.Executor
import java.util.concurrent.Executors

interface TestKoraAppTagged {
    @Repository(executorTag = Tag(ExampleTag::class))
    interface TestRepository : JdbcRepository {
        @Query("INSERT INTO table(value) VALUES (:value)")
        suspend fun abstractMethod(value: String?)
    }

    class ExampleTag

    @Tag(ExampleTag::class)
    fun jdbcQueryExecutorAccessor(): JdbcConnectionFactory? {
        return Mockito.mock(JdbcConnectionFactory::class.java)
    }

    @Tag(ExampleTag::class)
    fun executor(): Executor {
        return Executors.newCachedThreadPool()
    }
}
