package io.koraframework.ksp.common

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import org.slf4j.Logger
import org.slf4j.event.Level

object LogUtils {

    fun logElementsFull(logger: Logger, level: Level, prefix: String, elements: Collection<KSAnnotated>) {
        if (elements.isNotEmpty() && logger.isEnabledForLevel(level)) {
            val out = elements.joinToString("\n") { (it as? KSDeclaration)?.qualifiedName?.asString() ?: it.toString() }
                .prependIndent("    ")

            logger.makeLoggingEventBuilder(level).log("$prefix:\n{}", out)
        }
    }

    fun logElementsSimple(logger: Logger, level: Level, prefix: String, elements: Collection<KSAnnotated>) {
        if (elements.isNotEmpty() && logger.isEnabledForLevel(level)) {
            val out = elements.joinToString(", ") { (it as? KSDeclaration)?.simpleName?.asString() ?: it.toString() }
                .prependIndent("    ")

            logger.makeLoggingEventBuilder(level).log("$prefix:\n{}", out)
        }
    }
}
