package ru.tinkoff.kora.kora.app.ksp.exception

import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

data class CircularDependencyException(
    val cycle: List<String>,
    val source: ComponentDeclaration
) : ProcessingErrorException(
    String.format("There's a cycle in graph: \n\t%s", cycle.joinToString("\n\t")),
    source.source
)
