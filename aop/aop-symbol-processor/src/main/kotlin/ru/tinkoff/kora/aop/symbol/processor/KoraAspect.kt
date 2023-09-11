package ru.tinkoff.kora.aop.symbol.processor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName

interface KoraAspect {
    fun getSupportedAnnotationTypes(): Set<String>

    interface FieldFactory {
        fun constructorParam(type: TypeName, annotations: List<AnnotationSpec>): String
        fun constructorParam(type: KSType, annotations: List<AnnotationSpec>): String = constructorParam(type.toTypeName(), annotations)
        fun constructorInitialized(type: TypeName, initializer: CodeBlock): String
        fun constructorInitialized(type: KSType, initializer: CodeBlock): String = constructorInitialized(type.toTypeName(), initializer)
    }

    interface ApplyResult {
        enum class Noop : ApplyResult {
            INSTANCE
        }

        data class MethodBody(val codeBlock: CodeBlock) : ApplyResult
    }

    data class AspectContext(val fieldFactory: FieldFactory)

    fun KSFunctionDeclaration.superCall(superName: String) = superCall(superName, this.parameters.map { it.name?.asString().toString() })

    fun superCall(superName: String, params: Iterable<String>): CodeBlock {
        val b = CodeBlock.builder()
            .add(superName)
            .add("(")
        for ((i, parameter) in params.withIndex()) {
            if (i > 0) {
                b.add(", ")
            }
            b.add("%N", parameter)
        }
        b.add(")")
        return b.build()
    }

    fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: AspectContext): ApplyResult
}
