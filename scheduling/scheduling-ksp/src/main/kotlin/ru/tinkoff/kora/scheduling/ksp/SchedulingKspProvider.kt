package ru.tinkoff.kora.scheduling.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class SchedulingKspProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = SchedulingKsp(environment)
}
