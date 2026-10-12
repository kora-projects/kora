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

    private fun addParentCondition(conditions: Set<ClassName>): Boolean {
        if (this.parentConditions.contains(unconditionally)) {
            return false
        }
        if (conditions.contains(unconditionally)) {
            this.parentConditions.clear()
            this.parentConditions.add(unconditionally)
            return true
        }
        var changed = false
        for (condition in conditions) {
            if (condition != this.declaration.condition) {
                changed = this.parentConditions.add(condition) || changed
            }
        }
        return changed
    }

    /**
     * Root is created regardless of conditions of components that depend on it
     */
    fun markRoot() {
        this.addParentCondition(setOf(unconditionally))
    }

    fun nodeRef(inHolder: String): CodeBlock {
        if (inHolder == holderName) {
            return CodeBlock.of("%N", fieldName)
        } else {
            return CodeBlock.of("%N.%N", holderName, fieldName)
        }
    }

    /**
     * @return true if conditions of any dependency have changed
     */
    fun processCondition(): Boolean {
        val condition = when {
            this.declaration.condition == null && this.parentConditions.isEmpty() -> setOf(unconditionally)
            this.declaration.condition == null -> this.parentConditions
            else -> {
                val set = HashSet(this.parentConditions)
                set.add(this.declaration.condition)
                set.remove(unconditionally)
                set
            }
        }

        var changed = false
        fun add(component: ResolvedComponent?) {
            if (component != null) {
                changed = component.addParentCondition(condition) || changed
            }
        }
        for (dependency in this.dependencies) {
            when (dependency) {
                is ComponentDependency.NullDependency -> {}
                is ComponentDependency.TypeOfDependency -> {}
                is ComponentDependency.PromisedProxyParameterDependency -> add(dependency.realDependency)
                is ComponentDependency.PromiseOfDependency -> add(dependency.component)
                is ComponentDependency.AllOfDependency -> {
                    for (d in dependency.resolvedDependencies) {
                        add(d.component)
                    }
                }

                is ComponentDependency.TargetDependency -> add(dependency.component)
                is ComponentDependency.ValueOfDependency -> add(dependency.component)
                is ComponentDependency.WrappedTargetDependency -> add(dependency.component)
                is ComponentDependency.OneOfDependency -> {
                    for (d in dependency.dependencies) {
                        add(d.component)
                    }
                }

                is ComponentDependency.GraphDependency -> {}
            }
        }
        return changed
    }


    companion object {
        val unconditionally = ResolvedComponent::class.asClassName()
    }
}
