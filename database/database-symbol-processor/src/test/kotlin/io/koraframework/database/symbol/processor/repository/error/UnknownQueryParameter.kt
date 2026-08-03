package io.koraframework.database.symbol.processor.repository.error

import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcRepository

@Repository
interface UnknownQueryParameter : JdbcRepository {

    @Query("SELECT * FROM table WHERE id = :userId")
    fun wrongParameterName(id: Long): String
}
