package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
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
            c.add("%T::class", ksType.toClassName())
        }
        return c.add("]").build()
    }

}
