package ru.tinkoff.kora.kora.app.ksp.exception

import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependency
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.stream.Collectors

data class DuplicateDependencyException(
    val claim: DependencyClaim,
    val declaration: ComponentDeclaration,
    val foundDeclarations: List<ComponentDeclaration>
) : ProcessingErrorException(
    listOf(getErrorForDeclarations(claim, declaration, foundDeclarations))
) {

    constructor(
        foundDeclarations: List<ComponentDependency.SingleDependency>,
        claim: DependencyClaim,
        declaration: ComponentDeclaration
    ) : this(
        claim, declaration, foundDeclarations.map { it.component!!.declaration }.toList()
    )

    companion object {
        private fun getErrorForDeclarations(
            claim: DependencyClaim,
            declaration: ComponentDeclaration,
            foundDeclarations: List<ComponentDeclaration>
        ): ProcessingError {
            val deps = foundDeclarations
                .map { String.format("- %s", it.declarationString()) }
                .joinToString("\n", "Candidates for injection:\n", "").prependIndent("  ")

            return getError(claim, declaration, deps)
        }

        private fun getErrorForDependencies(
            claim: DependencyClaim,
            declaration: ComponentDeclaration,
            foundDeclarations: List<ComponentDependency.SingleDependency>
        ): ProcessingError {
            val deps: String = foundDeclarations
                .map { it.component!!.declaration }
                .map { String.format("- %s", it.declarationString()) }
                .joinToString("\n", "Candidates for injection:\n", "").prependIndent("  ")

            return getError(claim, declaration, deps)
        }

        private fun getError(
            claim: DependencyClaim,
            declaration: ComponentDeclaration,
            deps: String
        ): ProcessingError {
            if (claim.tags.isEmpty()) {
                return ProcessingError(
                    """More than one component matches dependency type: ${claim.type.toTypeName()} (no tags)
                    $deps
                    Please check that injection dependency is declared correctly or that @DefaultComponent annotation is not missing if was intended.""".trimIndent(),
                    declaration.source
                )
            } else {
                val tagMsg: String = claim.tags.stream()
                    .collect(Collectors.joining(", ", "@Tag(", ")"))
                return ProcessingError(
                    """More than one component matches dependency type: ${claim.type.toTypeName()} with $tagMsg
                    $deps
                    Please check that injection dependency is declared correctly or that @DefaultComponent annotation is not missing if was intended.""".trimIndent(),
                    declaration.source
                )
            }
        }
    }
}
