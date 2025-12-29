package ru.tinkoff.kora.kora.app.ksp.exception

import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.stream.Collectors

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
            val deps = cycle
                .map { String.format("- %s", it.declarationString()) }
                .joinToString("\n", "Cycle dependency candidates:\n", "").prependIndent("  ")

            if (declaration.tags.isEmpty()) {
                return ProcessingError(
                    """Encountered circular dependency in graph for source type: ${declaration.type.toTypeName()} (no tags)
                    $deps
                    Please check that you are not using cycle dependency in ${CommonClassNames.lifecycle}, this is forbidden.""".trimIndent(),
                    declaration.source
                )
            } else {
                val tagMsg: String = declaration.tags.stream()
                    .collect(Collectors.joining(", ", "@Tag(", ")"))
                return ProcessingError(
                    """Encountered circular dependency in graph for source type: ${declaration.type.toTypeName()} with $tagMsg
                    $deps
                    Please check that you are not using cycle dependency in ${CommonClassNames.lifecycle}, this is forbidden.""".trimIndent(),
                    declaration.source
                )
            }
        }
    }
}
