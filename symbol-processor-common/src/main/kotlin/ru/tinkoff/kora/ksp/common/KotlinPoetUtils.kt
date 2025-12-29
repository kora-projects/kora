package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName

object KotlinPoetUtils {
    inline fun FunSpec.Builder.controlFlow(controlFlow: String, vararg args: Any, callback: FunSpec.Builder.() -> Unit): FunSpec.Builder {
        this.beginControlFlow(controlFlow, *args)
        callback(this)
        return this.endControlFlow()
    }

    inline fun CodeBlock.Builder.controlFlow(controlFlow: String, vararg args: Any, callback: CodeBlock.Builder.() -> Unit): CodeBlock.Builder {
        this.beginControlFlow(controlFlow, *args)
        callback(this)
        return this.endControlFlow()
    }

    inline fun CodeBlock.Builder.nextControlFlow(controlFlow: String, vararg args: Any, callback: CodeBlock.Builder.() -> Unit): CodeBlock.Builder {
        this.nextControlFlow(controlFlow, args)
        callback(this)
        return this
    }

    fun List<KSType>.writeTagValue(name: String? = null): CodeBlock {
        val c = CodeBlock.builder()
        if (name != null) {
            c.add("%L = ", name)
        }
        c.add("[")
        for ((i, ksType) in this.withIndex()) {
            if (i > 0) {
                c.add(", ")
            }
            c.add("%T::class", ksType.declaration.let { it as KSClassDeclaration }.toClassName())
        }
        return c.add("]").build()
    }

    inline fun FunSpec.Builder.observe(observationName: String, result: TypeName, callback: CodeBlock.Builder.() -> Unit): FunSpec.Builder {
        val b = CodeBlock.builder()
        b.observe(observationName, result, callback)
        this.addCode(b.build())
        return this
    }


    inline fun CodeBlock.Builder.observe(observationName: String, result: TypeName, callback: CodeBlock.Builder.() -> Unit) = controlFlow(
        "%T.where(%T.VALUE, %N).where(%T.VALUE, %T.current().with(%N.span())).call<%T, %T>()",
        ScopedValue::class.asClassName(),
        ClassName("ru.tinkoff.kora.common.telemetry", "Observation"),
        observationName,
        ClassName("ru.tinkoff.kora.common.telemetry", "OpentelemetryContext"),
        ClassName("io.opentelemetry.context", "Context"),
        observationName,
        result,
        RuntimeException::class.asClassName(),
    ) { callback(this) }

}
