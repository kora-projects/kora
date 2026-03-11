package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.koraframework.kora.app.ksp.component.ResolvedComponents
import io.koraframework.kora.app.ksp.declaration.ComponentDeclarations

class ResolvedGraph(
    val root: KSClassDeclaration,
    val allModules: List<KSClassDeclaration>,
    val declarations: ComponentDeclarations,
    val components: ResolvedComponents
) {}
