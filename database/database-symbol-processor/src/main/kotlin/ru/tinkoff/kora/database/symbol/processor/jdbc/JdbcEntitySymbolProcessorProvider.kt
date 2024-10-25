package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class JdbcEntitySymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = JdbcEntitySymbolProcessor(environment)
}
