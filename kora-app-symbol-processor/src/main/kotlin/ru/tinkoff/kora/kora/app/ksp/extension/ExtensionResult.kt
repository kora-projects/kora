package ru.tinkoff.kora.kora.app.ksp.extension

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock

sealed interface ExtensionResult {
    class GeneratedResult(val constructor: KSFunctionDeclaration, val type: KSFunction) : ExtensionResult

    class CodeBlockResult(
        val source: KSDeclaration,
        val codeBlock: (CodeBlock) -> CodeBlock,
        val componentType: KSType,
        val componentTag: String?,
        val dependencyTypes: List<KSType>,
        val dependencyTags: List<String?>
    ) : ExtensionResult {
    }

    companion object {
        fun fromConstructor(constructor: KSFunctionDeclaration, type: KSClassDeclaration): ExtensionResult {
            return GeneratedResult(
                constructor,
                constructor.asMemberOf(type.asType(listOf()))
            )
        }

        fun fromExecutable(constructor: KSFunctionDeclaration, type: KSFunction): ExtensionResult {
            return GeneratedResult(
                constructor,
                type
            )
        }
    }

}
