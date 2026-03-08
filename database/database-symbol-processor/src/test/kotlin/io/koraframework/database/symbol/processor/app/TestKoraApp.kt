package io.koraframework.database.symbol.processor.app

import org.mockito.Mockito
import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root
import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcConnectionFactory
import io.koraframework.database.jdbc.JdbcRepository

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
