package ru.tinkoff.kora.kora.app.ksp.exception

import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

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
            val deps = cycle.joinToString("\n", "Cycle dependency candidates:\n", "") { String.format("- %s", it.declarationString()) }.prependIndent("  ")

            if (declaration.tag == null) {
                return ProcessingError(
                    """Encountered circular dependency in graph for source type: ${declaration.type.toTypeName()} (no tags)
                    $deps
                    Please check that you are not using cycle dependency in ${CommonClassNames.lifecycle}, this is forbidden.""".trimIndent(),
                    declaration.source
                )
            } else {
                return ProcessingError(
                    """Encountered circular dependency in graph for source type: ${declaration.type.toTypeName()} with @Tag(${declaration.tag}::class)
                    $deps
                    Please check that you are not using cycle dependency in ${CommonClassNames.lifecycle}, this is forbidden.""".trimIndent(),
                    declaration.source
                )
            }
        }
    }
}
