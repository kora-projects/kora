package ru.tinkoff.kora.kora.app.ksp.exception

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.kora.app.ksp.ProcessingState
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import javax.tools.Diagnostic

data class UnresolvedDependencyException(
    override val message: String,
    val forElement: KSDeclaration,
    val missingType: KSType,
    val missingTag: Set<String>,
    override val errors: List<ProcessingError> = listOf(ProcessingError(message.trimIndent(), forElement, Diagnostic.Kind.ERROR)),
    val resolving: ProcessingState.Processing? = null
) : ProcessingErrorException(errors) {
    constructor(
        forElement: KSDeclaration,
        missingType: KSType,
        missingTag: Set<String>,
        errors: List<ProcessingError>
    ) : this(toMessage(errors), forElement, missingType, missingTag, errors, null)

    override fun fillInStackTrace(): Throwable {
        return this
    }
}
