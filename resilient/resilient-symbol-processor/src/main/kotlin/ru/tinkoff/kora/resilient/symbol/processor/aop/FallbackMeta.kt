package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import javax.tools.Diagnostic

data class FallbackMeta(val method: String, val arguments: List<String>) {

    fun call(): String = toString()

    override fun toString(): String = method + "(" + arguments.joinToString(",") + ")"
}

fun KSAnnotation.asFallback(sourceMethod: KSFunctionDeclaration): FallbackMeta {
    val fallbackSignature = arguments.asSequence()
        .filter { arg -> arg.name!!.getShortName() == "method" }
        .map { arg -> arg.value.toString().trim() }
        .filter { it.isNotEmpty() }
        .first()

    return asFallback(sourceMethod, fallbackSignature)
}

fun KSAnnotation.asFallback(sourceMethod: KSFunctionDeclaration, fallbackSignature: String): FallbackMeta {
    val argStarted = fallbackSignature.indexOf('(')
    val argEnd = fallbackSignature.indexOf(')')
    if (argStarted == -1 || argEnd == -1) {
        throw ProcessingErrorException(
            ProcessingError(
                "Fallback method doesn't have proper signature like 'myMethod()' or 'myMethod(arg1, arg2)' but was: $fallbackSignature",
                null,
                Diagnostic.Kind.ERROR,
            )
        )
    }

    val sourceArgs = sourceMethod.parameters.asSequence()
        .map { p -> p.name!!.getShortName() }
        .toSet()

    val fallbackArgs = fallbackSignature.substring(argStarted + 1, fallbackSignature.length - 1).split(",").asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    if (fallbackArgs.isNotEmpty()) {
        val illegalArgs = fallbackArgs.stream()
            .filter { !sourceArgs.contains(it) }
            .toList()

        if (illegalArgs.isNotEmpty()) {
            throw ProcessingErrorException(
                ProcessingError(
                    "Fallback method specifies illegal arguments $illegalArgs, available arguments: $sourceArgs",
                    null,
                    Diagnostic.Kind.ERROR
                )
            )
        }
    }

    return FallbackMeta(fallbackSignature.substring(0, argStarted), fallbackArgs)
}
