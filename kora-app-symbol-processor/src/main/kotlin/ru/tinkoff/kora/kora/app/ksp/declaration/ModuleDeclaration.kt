package ru.tinkoff.kora.kora.app.ksp.declaration

import com.google.devtools.ksp.symbol.KSClassDeclaration

sealed interface ModuleDeclaration {
    val element: KSClassDeclaration

    data class MixedInModule(override val element: KSClassDeclaration) : ModuleDeclaration
    data class AnnotatedModule(override val element: KSClassDeclaration) : ModuleDeclaration
}
