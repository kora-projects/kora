package ru.tinkoff.kora.ksp.common.exception

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import javax.tools.Diagnostic

data class ProcessingError(val message: String, private val declaration: KSAnnotated?, private val kind: Diagnostic.Kind = Diagnostic.Kind.ERROR) {

    fun print(kspLogger: KSPLogger) {
        when (this.kind) {
            Diagnostic.Kind.OTHER -> kspLogger.info(this.message, declaration)
            Diagnostic.Kind.NOTE -> kspLogger.info(this.message, declaration)
            Diagnostic.Kind.WARNING -> kspLogger.warn(this.message, declaration)
            Diagnostic.Kind.MANDATORY_WARNING -> kspLogger.warn(this.message, declaration)
            Diagnostic.Kind.ERROR -> kspLogger.error(this.message, declaration)
            else -> kspLogger.error(this.message, declaration)
        }
    }

    companion object {
        fun merge(e1: ProcessingError, e2: ProcessingError): ProcessingError {
            if (e1.message.isEmpty()) {
                return e2
            }
            if (e2.message.isEmpty()) {
                return e1
            }
            val (root, child) = if (e1.kind == Diagnostic.Kind.ERROR) e1 to e2 else e2 to e1

            return ProcessingError(
                root.message + "\n" + child.message, root.declaration, root.kind
            )
        }
    }
}
