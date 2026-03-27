package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import io.koraframework.kora.app.ksp.component.ResolvedComponent

class ResolvedGraph(
    val root: KSClassDeclaration,
    val allModules: List<KSClassDeclaration>,
    val components: List<ResolvedComponent>,
    val conditionByTag: MutableMap<ClassName, ResolvedComponent>
) {}
