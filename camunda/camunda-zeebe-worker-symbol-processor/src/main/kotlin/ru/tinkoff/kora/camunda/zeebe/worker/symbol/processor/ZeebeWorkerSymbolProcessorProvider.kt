package ru.tinkoff.kora.camunda.zeebe.worker.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class ZeebeWorkerSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment) = ZeebeWorkerSymbolProcessor(environment)
}
