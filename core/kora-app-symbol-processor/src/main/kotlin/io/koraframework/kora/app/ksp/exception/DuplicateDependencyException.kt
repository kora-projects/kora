package io.koraframework.kora.app.ksp.exception

import com.google.devtools.ksp.processing.Resolver
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.component.DependencyClaim
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException

class DuplicateDependencyException(
    resolver: Resolver,
    val claim: DependencyClaim,
    val declaration: ComponentDeclaration,
    val foundDeclarations: List<ComponentDeclaration>
) : ProcessingErrorException(
    listOf(getError(resolver, claim, declaration, foundDeclarations))
) {

    companion object {
        private fun getError(
            resolver: Resolver,
            claim: DependencyClaim,
            declaration: ComponentDeclaration,
            foundDeclarations: List<ComponentDeclaration>
        ): ProcessingError {
            val msg = StringBuilder()
            msg.append("Multiple components match dependency:\n  ").append(claim.type.toTypeName())
            msg.append(DependencySourceFormatter.tagSuffix(resolver, claim.tag))
            msg.append(DependencySourceFormatter.requiredAtSection(declaration, claim.source))
            msg.append("\n\nCandidates:")
            for (candidate in foundDeclarations) {
                msg.append("\n  - ").append(candidate.type.toTypeName())
                    .append(DependencySourceFormatter.tagSuffix(resolver, candidate.tag))
                    .append(" from ").append(candidate.declarationString())
            }
            msg.append("\n\nFix:")
            msg.append("\n  - Add different @Tag(...) annotations to candidates and request the needed tag.")
            msg.append("\n  - Mark fallback candidate with @DefaultComponent.")
            msg.append("\n  - Remove one duplicate provider.")
            return ProcessingError(msg.toString(), claim.source ?: declaration.source)
        }
    }
}
