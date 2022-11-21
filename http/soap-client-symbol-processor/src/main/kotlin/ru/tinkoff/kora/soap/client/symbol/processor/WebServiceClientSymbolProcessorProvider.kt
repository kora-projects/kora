package ru.tinkoff.kora.soap.client.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

@OptIn(KspExperimental::class)
class WebServiceClientSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): WebServiceClientSymbolProcessor {
        return WebServiceClientSymbolProcessor(environment)
    }
}
