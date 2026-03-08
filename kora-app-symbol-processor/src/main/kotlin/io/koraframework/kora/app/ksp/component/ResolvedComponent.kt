package io.koraframework.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSType
import io.koraframework.kora.app.ksp.KoraAppProcessor
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration

class ResolvedComponent(
    val index: Int,
    val declaration: ComponentDeclaration,
    val type: KSType,
    val tag: String?,
    val dependencies: List<ComponentDependency>
) {
    val fieldName = "component${index}"
    val holderName = "holder${index / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS}"
}
