package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class CassandraEntitySymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = CassandraEntitySymbolProcessor(environment)
}
