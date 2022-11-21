package ru.tinkoff.kora.database.symbol.processor.repository

import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository

@Repository
interface QueryFromResourceRepository : JdbcRepository{
    @Query("classpath:/sql/test-query.sql")
    fun test()
}
