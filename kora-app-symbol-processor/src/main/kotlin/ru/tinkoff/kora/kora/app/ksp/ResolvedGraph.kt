package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponents
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclarations

class ResolvedGraph(
    val root: KSClassDeclaration,
    val allModules: List<KSClassDeclaration>,
    val declarations: ComponentDeclarations,
    val components: ResolvedComponents
) {}
