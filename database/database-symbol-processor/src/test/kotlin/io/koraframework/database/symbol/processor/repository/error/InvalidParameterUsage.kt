package io.koraframework.database.symbol.processor.repository.error

import io.koraframework.database.common.annotation.Query
import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcRepository

@Repository
interface InvalidParameterUsage : JdbcRepository {
    @Query("SELECT * FROM table WHERE field3 = :param1.field3")
    fun wrongFieldUsedInTemplate(param1: Dto?, param2: String?): String?

    data class Dto(val field3: String)
}
