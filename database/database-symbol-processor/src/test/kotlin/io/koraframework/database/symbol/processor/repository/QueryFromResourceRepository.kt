package io.koraframework.database.symbol.processor.repository

import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcRepository

@Repository
interface QueryFromResourceRepository : JdbcRepository{
    @Query("classpath:/sql/test-query.sql")
    fun test()
}
