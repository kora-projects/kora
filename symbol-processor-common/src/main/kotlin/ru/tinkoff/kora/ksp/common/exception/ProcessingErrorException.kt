package ru.tinkoff.kora.ksp.common.exception

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import javax.tools.Diagnostic


open class ProcessingErrorException constructor(open val errors: List<ProcessingError>) : RuntimeException(toMessage(errors)) {

    constructor(error: ProcessingError) : this(listOf(error))

    constructor(message: String, declaration: KSAnnotated) : this(
        listOf(
            ProcessingError(
                message,
                declaration,
                Diagnostic.Kind.ERROR
            )
        )
    )

    fun printError(kspLogger: KSPLogger) {
        for (error in errors) {
            error.print(kspLogger)
        }
    }

    companion object {
        fun toMessage(errors: List<ProcessingError>): String {
            return errors.joinToString("\n") { obj: ProcessingError -> obj.message }
        }

        fun merge(exceptions: List<ProcessingErrorException>): ProcessingErrorException {
            val errors = exceptions
                .map { obj: ProcessingErrorException -> obj.errors }
                .flatten()
                .toList()
            val exception = ProcessingErrorException(errors)
            for (processingErrorException in exceptions) {
                exception.addSuppressed(processingErrorException)
            }
            return exception
        }
    }

}
