package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.kora.app.ksp.extension.Extensions

class ProcessingContext(
    var resolver: Resolver,
    val kspLogger: KSPLogger,
    val codeGenerator: CodeGenerator
) {
    val serviceTypesHelper = ServiceTypesHelper(resolver)
    val extensions = Extensions.load(ProcessingContext::class.java.classLoader, resolver, kspLogger, codeGenerator)
    val dependencyHintProvider = DependencyModuleHintProvider()
}
