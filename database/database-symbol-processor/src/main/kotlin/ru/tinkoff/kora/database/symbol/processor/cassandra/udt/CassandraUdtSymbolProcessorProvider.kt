package ru.tinkoff.kora.database.symbol.processor.cassandra.udt

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class CassandraUdtSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = CassandraUdtSymbolProcessor(environment)
}
