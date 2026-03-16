package io.koraframework.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName
import io.koraframework.kora.app.ksp.KoraAppProcessor
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration


class ResolvedComponent(
    private var idx: Int,
    val declaration: ComponentDeclaration,
    val type: KSType,
    val tag: String?,
    val dependencies: List<ComponentDependency>
) {
    var fieldName = "component${idx}"
    var holderName = "holder${idx / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS}"
    private val parentConditions: MutableSet<ClassName> = HashSet()

    val index get() = idx

    fun setIndex(index: Int) {
        this.idx = index
        fieldName = "component${idx}"
        holderName = "holder${idx / KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS}"
    }

    fun getParentConditions(): Set<ClassName> {
        if (this.parentConditions.contains(unconditionally)) {
            return emptySet()
        }
        return parentConditions
    }

    private fun addParentCondition(conditions: Set<ClassName>) {
        if (this.parentConditions.contains(unconditionally)) {
            return
        }
        if (conditions.contains(unconditionally)) {
            this.parentConditions.clear()
            this.parentConditions.add(unconditionally)
            return
        }
        this.parentConditions.addAll(conditions)
        if (this.declaration.condition != null) {
            this.parentConditions.remove(this.declaration.condition)
        }
    }


    fun nodeRef(inHolder: String): CodeBlock {
        if (inHolder == holderName) {
            return CodeBlock.of("%N", fieldName)
        } else {
            return CodeBlock.of("%N.%N", holderName, fieldName)
        }
    }

    fun processCondition() {
        val condition = when {
            this.declaration.condition == null && this.parentConditions.isEmpty() -> setOf(unconditionally)
            this.declaration.condition == null -> this.parentConditions
            else -> {
                val set = HashSet(this.parentConditions)
                set.add(this.declaration.condition)
                set
            }
        }

        for (dependency in this.dependencies) {
            when (dependency) {
                is ComponentDependency.NullDependency -> {}
                is ComponentDependency.TypeOfDependency -> {}
                is ComponentDependency.PromisedProxyParameterDependency -> {}
                is ComponentDependency.PromiseOfDependency -> dependency.component?.addParentCondition(condition)
                is ComponentDependency.AllOfDependency -> {
                    for (d in dependency.resolvedDependencies) {
                        d.component?.addParentCondition(condition)
                    }
                }

                is ComponentDependency.TargetDependency -> dependency.component.addParentCondition(condition)
                is ComponentDependency.ValueOfDependency -> dependency.component.addParentCondition(condition)
                is ComponentDependency.WrappedTargetDependency -> dependency.component.addParentCondition(condition)
                is ComponentDependency.OneOfDependency -> {
                    for (d in dependency.dependencies) {
                        d.component?.addParentCondition(condition)
                    }
                }
            }
        }
    }


    companion object {
        val unconditionally = ResolvedComponent::class.asClassName()
    }
}
