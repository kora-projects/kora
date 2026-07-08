package io.koraframework.database.symbol.processor.app

import io.koraframework.common.annotation.Tag
import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcExecutor
import io.koraframework.database.jdbc.JdbcRepository
import org.mockito.Mockito
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
    fun jdbcQueryExecutorAccessor(): JdbcExecutor? {
        return Mockito.mock(JdbcExecutor::class.java)
    }

    @Tag(ExampleTag::class)
    fun executor(): Executor {
        return Executors.newCachedThreadPool()
    }
}
