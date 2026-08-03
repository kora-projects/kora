package io.koraframework.database.symbol.processor.repository.error

import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcRepository

@Repository
interface UnknownEntityField : JdbcRepository {

    @Query("SELECT * FROM table WHERE name = :dto.name")
    fun wrongEntityField(dto: Dto): String

    data class Dto(val id: Long)
}
