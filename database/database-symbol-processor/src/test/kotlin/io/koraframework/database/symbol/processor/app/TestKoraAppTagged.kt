package io.koraframework.database.symbol.processor.app

import org.mockito.Mockito
import io.koraframework.common.Tag
import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcConnectionFactory
import io.koraframework.database.jdbc.JdbcRepository
import java.util.concurrent.Executor
import java.util.concurrent.Executors

interface TestKoraAppTagged {
    @Repository(executorTag = ExampleTag::class)
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
