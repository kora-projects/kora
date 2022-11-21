package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

@KspExperimental
class KoraAppProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): KoraAppProcessor {
        return KoraAppProcessor(environment)
    }
}
