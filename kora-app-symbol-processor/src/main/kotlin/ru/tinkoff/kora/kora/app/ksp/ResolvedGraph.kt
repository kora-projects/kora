package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent

data class ResolvedGraph(val root: KSClassDeclaration, val allModules: List<KSClassDeclaration>, val components: List<ResolvedComponent>)
