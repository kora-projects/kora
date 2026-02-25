package ru.tinkoff.kora.kora.app.ksp.component

import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.kora.app.ksp.declaration.DeclarationWithIndex


class ResolvedComponents() {
    private val declarationIndexToResolvedComponent = LinkedHashMap<Int, ResolvedComponent>()
    val size get() = declarationIndexToResolvedComponent.size

    constructor(that: ResolvedComponents) : this() {
        declarationIndexToResolvedComponent.putAll(that.declarationIndexToResolvedComponent)
    }


    fun getByDeclarationIndex(idx: Int): ResolvedComponent? {
        return declarationIndexToResolvedComponent[idx]
    }

    fun getByDeclaration(d: DeclarationWithIndex): ResolvedComponent? {
        return declarationIndexToResolvedComponent[d.index]
    }

    fun add(declarationIdx: Int, declaration: ComponentDeclaration, resolvedDependencies: List<ComponentDependency>) {
        declarationIndexToResolvedComponent[declarationIdx] = ResolvedComponent(
            declarationIndexToResolvedComponent.size, declaration, declaration.type, declaration.tag, resolvedDependencies
        )
    }

    fun components(): Collection<ResolvedComponent> {
        return declarationIndexToResolvedComponent.values
    }
}
