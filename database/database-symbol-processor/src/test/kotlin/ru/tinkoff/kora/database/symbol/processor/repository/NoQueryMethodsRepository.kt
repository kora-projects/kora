package ru.tinkoff.kora.database.symbol.processor.repository

import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository

@Repository
interface NoQueryMethodsRepository : JdbcRepository
