package ru.tinkoff.kora.database.symbol.processor.repository

import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository

@Repository
abstract class AbstractClassRepository(private val field: String?) : JdbcRepository {
    @Query("INSERT INTO table(value) VALUES (:value)")
    abstract fun abstractMethod(value: String?)
    fun nonAbstractMethod() {}
}
