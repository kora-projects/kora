package io.koraframework.kora.app.ksp.exception

import com.google.devtools.ksp.processing.Resolver
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.component.DependencyClaim
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException

/**
 * @param cycle     components forming the cycle, the last one is the component that closes the cycle
 * @param requester component whose dependency closes the cycle
 * @param claim     dependency of the requester that closes the cycle
 * @param note      why the cycle cannot be broken automatically
 * @param fix       fix specific to the reason from note
 */
class CircularDependencyException(
    resolver: Resolver,
    val cycle: List<ComponentDeclaration>,
    val declaration: ComponentDeclaration,
    val requester: ComponentDeclaration,
    val claim: DependencyClaim,
    val note: String?,
    val fix: String?
) : ProcessingErrorException(
    getError(resolver, cycle, declaration, requester, claim, note, fix)
) {

    companion object {
        private fun getError(
            resolver: Resolver,
            cycle: List<ComponentDeclaration>,
            declaration: ComponentDeclaration,
            requester: ComponentDeclaration,
            claim: DependencyClaim,
            note: String?,
            fix: String?
        ): ProcessingError {
            val msg = StringBuilder()
            msg.append("Circular dependency found:\n  ").append(declaration.type.toTypeName())
            msg.append(DependencySourceFormatter.tagSuffix(resolver, declaration.tag))
            msg.append("\n\nDependency cycle:")
            cycle.forEachIndexed { i, component ->
                msg.append(if (i == 0) "\n  @--- " else "\n  ^--- ").append(component.declarationString())
            }
            msg.append(" [CYCLE]")
            msg.append(DependencySourceFormatter.requiredAtSection(requester, claim.source))
            if (note != null) {
                msg.append("\n\nNote:\n  ").append(note)
            }
            msg.append("\n\nFix:")
            if (fix != null) {
                msg.append("\n  - ").append(fix)
            }
            msg.append("\n  - Break the cycle with ValueOf<T> or PromiseOf<T> where lazy access is valid.")
            msg.append("\n  - Move shared state into a separate component.")
            msg.append("\n  - Do not create dependency cycles in ${CommonClassNames.lifecycle.simpleName}.")
            return ProcessingError(msg.toString(), claim.source ?: declaration.source)
        }
    }
}
