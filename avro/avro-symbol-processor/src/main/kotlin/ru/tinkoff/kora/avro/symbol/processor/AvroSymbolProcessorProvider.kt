package ru.tinkoff.kora.avro.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class AvroSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): AvroSymbolProcessor {
        return AvroSymbolProcessor(environment)
    }
}
