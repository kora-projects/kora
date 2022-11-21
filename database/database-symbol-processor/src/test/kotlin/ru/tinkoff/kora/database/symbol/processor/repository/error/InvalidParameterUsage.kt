package ru.tinkoff.kora.database.symbol.processor.repository.error

import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository

@Repository
interface InvalidParameterUsage : JdbcRepository {
    @Query("SELECT * FROM table WHERE field3 = :param1.field3")
    fun wrongFieldUsedInTemplate(param1: Dto?, param2: String?): String?
    class Dto
}
