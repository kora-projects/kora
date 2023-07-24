package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependency
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependencyHelper
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.*

sealed interface ProcessingState {
    sealed interface ResolutionFrame {
        data class Root(val rootIndex: Int) : ResolutionFrame
        data class Component(
            val declaration: ComponentDeclaration,
            val dependenciesToFind: List<DependencyClaim> = ComponentDependencyHelper.parseDependencyClaim(declaration),
            val resolvedDependencies: MutableList<ComponentDependency> = ArrayList(dependenciesToFind.size),
            val currentDependency: Int = 0
        ) : ResolutionFrame
    }

    fun stack() = if (this is Processing) {
        this.resolutionStack
    } else {
        ArrayDeque()
    }

    data class None(
        val root: KSClassDeclaration,
        val allModules: List<KSClassDeclaration>,
        val sourceDeclarations: MutableList<ComponentDeclaration>,
        val templateDeclarations: MutableList<ComponentDeclaration>,
        val rootSet: List<ComponentDeclaration>
    ) :
        ProcessingState

    data class Processing(
        val root: KSClassDeclaration,
        val allModules: List<KSClassDeclaration>,
        val sourceDeclarations: MutableList<ComponentDeclaration>,
        val templateDeclarations: MutableList<ComponentDeclaration>,
        val rootSet: List<ComponentDeclaration>,
        val resolvedComponents: MutableList<ResolvedComponent>,
        val resolutionStack: Deque<ResolutionFrame>
    ) : ProcessingState {
        fun findResolvedComponent(declaration: ComponentDeclaration) = resolvedComponents.asSequence().filter { it.declaration === declaration }.firstOrNull()

    }

    data class Ok(val root: KSClassDeclaration, val allModules: List<KSClassDeclaration>, val components: List<ResolvedComponent>) : ProcessingState
    data class NewRoundRequired(val source: Any, val type: KSType, val tag: Set<String>, val processing: Processing) : ProcessingState
    data class Failed(val exception: ProcessingErrorException, val resolutionStack: Deque<ResolutionFrame>) : ProcessingState
}
