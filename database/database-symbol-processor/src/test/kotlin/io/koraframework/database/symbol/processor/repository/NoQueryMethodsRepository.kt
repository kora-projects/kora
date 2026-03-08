package io.koraframework.database.symbol.processor.repository

import io.koraframework.database.common.annotation.Repository
import io.koraframework.database.jdbc.JdbcRepository

@Repository
interface NoQueryMethodsRepository : JdbcRepository
