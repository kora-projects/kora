package ru.tinkoff.kora.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessor
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration

data class ResolvedComponent(
    val index: Int,
    val declaration: ComponentDeclaration,
    val type: KSType,
    val tags: Set<String>,
    val templateParams: List<KSType>,
    val dependencies: List<ComponentDependency>
) {
    val fieldName = "component${index}"
    val holderName = "holder${index / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS}"
}
