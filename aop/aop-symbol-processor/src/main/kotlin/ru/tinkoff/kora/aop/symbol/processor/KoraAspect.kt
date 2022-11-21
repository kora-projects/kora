package ru.tinkoff.kora.aop.symbol.processor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock

interface KoraAspect {
    fun getSupportedAnnotationTypes(): Set<String>
    interface FieldFactory {
        fun constructorParam(type: KSType, annotations: List<AnnotationSpec>): String
        fun constructorInitialized(type: KSType, initializer: CodeBlock): String
    }

    interface ApplyResult {
        enum class Noop : ApplyResult {
            INSTANCE
        }

        data class MethodBody(val codeBlock: CodeBlock) : ApplyResult
    }

    data class AspectContext(val fieldFactory: FieldFactory)

    fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: AspectContext): ApplyResult
}
