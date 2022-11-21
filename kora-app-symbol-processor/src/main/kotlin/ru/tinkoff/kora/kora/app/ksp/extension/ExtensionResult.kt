package ru.tinkoff.kora.kora.app.ksp.extension

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

sealed interface ExtensionResult {
    class GeneratedResult(val constructor: KSFunctionDeclaration, val type: KSFunction) : ExtensionResult

    object RequiresCompilingResult : ExtensionResult

    companion object {
        fun fromConstructor(constructor: KSFunctionDeclaration, type: KSClassDeclaration): ExtensionResult {
            return GeneratedResult(
                constructor,
                constructor.asMemberOf(type.asStarProjectedType())
            )
        }
    }

}
