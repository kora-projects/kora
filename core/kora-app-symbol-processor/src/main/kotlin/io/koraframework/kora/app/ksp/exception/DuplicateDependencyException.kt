package io.koraframework.kora.app.ksp.exception

import com.squareup.kotlinpoet.ksp.toTypeName
import com.google.devtools.ksp.symbol.KSValueParameter
import io.koraframework.kora.app.ksp.component.ComponentDependency
import io.koraframework.kora.app.ksp.component.DependencyClaim
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException

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
                .joinToString("\n", "Candidates:\n", "").prependIndent("  ")

            return getError(claim, declaration, deps)
        }

        private fun getError(
            claim: DependencyClaim,
            declaration: ComponentDeclaration,
            deps: String
        ): ProcessingError {
            val msg = StringBuilder()
            msg.append("Multiple components match dependency:\n  ").append(claim.type.toTypeName())
            if (claim.tag == null) {
                msg.append(" (no tags)")
            } else {
                msg.append(" with @Tag(${claim.tag}::class)")
            }
            val source = claim.source
            if (source is KSValueParameter) {
                msg.append("\n\nRequired at:\n  ")
                    .append(source.parent)
                    .append("\n  parameter: ")
                    .append(source.type.toTypeName())
                    .append(" ")
                    .append(source.name?.asString() ?: "<unnamed>")
            }
            msg.append("\n\n").append(deps.trimEnd())
            msg.append("\n\nFix:")
            msg.append("\n  - Add different @Tag(...) annotations to candidates and request the needed tag.")
            msg.append("\n  - Mark fallback candidate with @DefaultComponent.")
            msg.append("\n  - Remove one duplicate provider.")
            return ProcessingError(msg.toString(), claim.source ?: declaration.source)
        }
    }
}
