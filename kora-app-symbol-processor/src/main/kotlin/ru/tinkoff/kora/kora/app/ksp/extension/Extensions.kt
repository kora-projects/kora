package ru.tinkoff.kora.kora.app.ksp.extension

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import java.util.*

data class Extensions(val extensions: List<KoraExtension>) {
    companion object {
        fun load(classLoader: ClassLoader, resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): Extensions {
            val serviceLoader = ServiceLoader.load(ExtensionFactory::class.java, classLoader)
            return Extensions(serviceLoader.mapNotNull { it.create(resolver, kspLogger, codeGenerator) })
        }
    }

    fun findExtension(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        val extensions = ArrayList<() -> ExtensionResult>()
        for (extension in this.extensions) {
            val generator = extension.getDependencyGenerator(resolver, type)
            if (generator != null) {
                extensions.add(generator)
            }
        }
        if (extensions.isEmpty()) {
            return null
        }
        if (extensions.size > 1) {
            // todo warning?
        }
        return extensions[0]
    }
}
