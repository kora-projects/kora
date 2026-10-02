package io.koraframework.kora.app.ksp.extension

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import org.slf4j.LoggerFactory
import java.util.*

data class Extensions(val extensions: List<KoraExtension>) {
    companion object {
        private val logger = LoggerFactory.getLogger(Extensions::class.java)

        fun load(classLoader: ClassLoader, resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): Extensions {
            val serviceLoader = ServiceLoader.load(ExtensionFactory::class.java, classLoader)
            val extensions = serviceLoader.mapNotNull { it.create(resolver, kspLogger, codeGenerator) }

            if (extensions.isNotEmpty() && logger.isInfoEnabled) {
                val out = extensions.joinToString("\n") { it.javaClass.canonicalName }.prependIndent("    ")

                logger.info("Extensions found:\n{}", out)
            }

            return Extensions(extensions)
        }
    }

    fun findExtension(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        val extensions = ArrayList<() -> ExtensionResult>()
        for (extension in this.extensions) {
            val generator = extension.getDependencyGenerator(resolver, type, tag)
            if (generator != null) {
                logger.trace("Extension '{}' is suitable generating for type: {}", extension.javaClass.canonicalName, type)
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
