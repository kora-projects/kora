package io.koraframework.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
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


    fun nodeRef(inHolder: String): CodeBlock {
        if (inHolder == holderName) {
            return CodeBlock.of("%N", fieldName)
        } else {
            return CodeBlock.of("%N.%N", holderName, fieldName)
        }
    }
}
