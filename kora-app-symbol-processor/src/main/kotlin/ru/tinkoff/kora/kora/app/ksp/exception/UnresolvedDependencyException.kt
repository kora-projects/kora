package ru.tinkoff.kora.kora.app.ksp.exception

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import javax.tools.Diagnostic

data class UnresolvedDependencyException(
    override val message: String,
    val forElement: KSDeclaration,
    val missingType: KSType,
    val missingTag: String?,
    override val errors: List<ProcessingError> = listOf(ProcessingError(message.trimIndent(), forElement, Diagnostic.Kind.ERROR)),
) : ProcessingErrorException(errors)
