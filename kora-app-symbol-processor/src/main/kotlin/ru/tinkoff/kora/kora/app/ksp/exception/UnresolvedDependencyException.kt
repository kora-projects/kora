package ru.tinkoff.kora.kora.app.ksp.exception

import ru.tinkoff.kora.kora.app.ksp.ProcessingState
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import javax.tools.Diagnostic

data class UnresolvedDependencyException(
    override val message: String,
    val component: ComponentDeclaration,
    val dependencyClaim: DependencyClaim,
    override val errors: List<ProcessingError> = listOf(ProcessingError(message.trimIndent(), component.source, Diagnostic.Kind.ERROR)),
    val resolving: ProcessingState.Processing? = null
) : ProcessingErrorException(errors) {
    constructor(
        forElement: ComponentDeclaration,
        dependencyClaim: DependencyClaim,
        errors: List<ProcessingError>,
    ) : this(toMessage(errors), forElement, dependencyClaim, errors, null)
}
