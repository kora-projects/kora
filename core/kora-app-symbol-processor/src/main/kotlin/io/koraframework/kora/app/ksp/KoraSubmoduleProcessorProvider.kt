package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class KoraSubmoduleProcessorProvider() : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = KoraSubmoduleProcessor(environment)
}
