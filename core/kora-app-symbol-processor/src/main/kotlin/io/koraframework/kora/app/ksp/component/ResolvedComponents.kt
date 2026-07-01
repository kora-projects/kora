package io.koraframework.kora.app.ksp.component

import com.squareup.kotlinpoet.ClassName
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.kora.app.ksp.declaration.DeclarationWithIndex


class ResolvedComponents() {
    private val declarationIndexToResolvedComponent = LinkedHashMap<Int, ResolvedComponent>()
    private var resolvedComponents = arrayOfNulls<ResolvedComponent>(10)
    private var currentIdx = 0

    val size get() = declarationIndexToResolvedComponent.size

    constructor(that: ResolvedComponents) : this() {
        declarationIndexToResolvedComponent.putAll(that.declarationIndexToResolvedComponent)
        resolvedComponents = that.resolvedComponents.copyOf()
        currentIdx = that.currentIdx
    }

    fun getByDeclarationIndex(idx: Int): ResolvedComponent? {
        return declarationIndexToResolvedComponent[idx]
    }

    fun getByDeclaration(d: DeclarationWithIndex): ResolvedComponent? {
        return declarationIndexToResolvedComponent[d.index]
    }

    fun add(declarationIdx: Int, declaration: ComponentDeclaration, resolvedDependencies: List<ComponentDependency>) {
        val component = ResolvedComponent(
            declarationIndexToResolvedComponent.size, declaration, declaration.type, declaration.tag, resolvedDependencies
        )
        declarationIndexToResolvedComponent[declarationIdx] = component
        if (currentIdx >= resolvedComponents.size) {
            resolvedComponents = resolvedComponents.copyOf(resolvedComponents.size * 2)
        }
        this.resolvedComponents[currentIdx] = component;
        currentIdx++;

    }

    fun processConditions(conditions: Map<ClassName, ResolvedComponent>) {
        fun maxDependencyIndex(dependency: ComponentDependency): Int {
            return when (dependency) {
                is ComponentDependency.PromisedProxyParameterDependency -> 0
                is ComponentDependency.PromiseOfDependency -> 0
                is ComponentDependency.TypeOfDependency -> 0
                is ComponentDependency.NullDependency -> 0
                is ComponentDependency.GraphDependency -> 0
                is ComponentDependency.AllOfDependency -> dependency.resolvedDependencies.maxOfOrNull { maxDependencyIndex(it) } ?: 0
                is ComponentDependency.OneOfDependency -> dependency.dependencies.maxOfOrNull { maxDependencyIndex(it) } ?: 0
                is ComponentDependency.TargetDependency -> dependency.component.index
                is ComponentDependency.ValueOfDependency -> dependency.component.index
                is ComponentDependency.WrappedTargetDependency -> dependency.component.index;
            };

        }
        for (condition in conditions.values) {
            val conditionIndex = condition.index
            if (conditionIndex == 0) {
                continue
            }
            if (condition.dependencies.isEmpty()) {
                resolvedComponents.copyInto(resolvedComponents, 1, 0, conditionIndex)
                resolvedComponents[0] = condition;
                for (i in 0..conditionIndex) {
                    resolvedComponents[i]?.setIndex(i)
                }
                return
            }
            val maxDependency = condition.dependencies
                .asSequence()
                .map { maxDependencyIndex(it) }
                .minOrNull() ?: -1
            require(conditionIndex > maxDependency)
            resolvedComponents.copyInto(resolvedComponents, maxDependency + 1, maxDependency + 2, conditionIndex - maxDependency - 1)
            resolvedComponents[maxDependency + 1] = condition;
            for (i in maxDependency + 1..conditionIndex) {
                resolvedComponents[i]?.setIndex(i)
            }
        }
    }

    fun components(): Sequence<ResolvedComponent> {
        return resolvedComponents.asSequence().filterNotNull()
    }

    fun componentsReversed(): Sequence<ResolvedComponent> {
        return sequence {
            for (i in currentIdx - 1 downTo 0) {
                yield(resolvedComponents[i]!!)
            }
        }
    }
}
