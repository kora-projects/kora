package io.koraframework.kora.app.ksp.exception

import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException

data class CircularDependencyException(
    val cycle: List<ComponentDeclaration>,
    val declaration: ComponentDeclaration
) : ProcessingErrorException(
    getError(cycle, declaration)
) {

    companion object {
        private fun getError(
            cycle: List<ComponentDeclaration>,
            declaration: ComponentDeclaration
        ): ProcessingError {
            val deps = cycle.joinToString("\n  ^--- ", "Dependency cycle:\n  @--- ") { it.declarationString() }.prependIndent("  ")

            val msg = StringBuilder()
            msg.append("Circular dependency found:\n  ").append(declaration.type.toTypeName())
            if (declaration.tag == null) {
                msg.append(" (no tags)")
            } else {
                msg.append(" with @Tag(${declaration.tag}::class)")
            }
            msg.append("\n\n").append(deps.trimEnd()).append(" [CYCLE]")
            msg.append("\n\nFix:")
            msg.append("\n  - Break the cycle with ValueOf<T> or PromiseOf<T> where lazy access is valid.")
            msg.append("\n  - Move shared state into a separate component.")
            msg.append("\n  - Do not create dependency cycles in ${CommonClassNames.lifecycle}.")
            return ProcessingError(msg.toString(), declaration.source)
        }
    }
}
