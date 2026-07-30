package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException
import javax.tools.Diagnostic

data class FallbackMeta(val method: String, val arguments: List<String>, val reasonType: KSType?) {

    fun call(): String = call("_e")

    fun call(reason: String): String {
        val args = ArrayList(arguments)
        if (reasonType != null) {
            args.add("($reason as ${reasonType.toTypeName()})")
        }
        return method + "(" + args.joinToString(", ") + ")"
    }

    fun reasonTypeName(): TypeName? = reasonType?.toTypeName()

    override fun toString(): String = call()
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

    val methodName = fallbackSignature.substring(0, argStarted)
    val fallbackMethods = (sourceMethod.parentDeclaration as? KSClassDeclaration)?.declarations
        ?.filterIsInstance<KSFunctionDeclaration>()
        ?.filter { it.simpleName.asString() == methodName }
        ?.toList()
        ?: emptyList()
    if (fallbackMethods.isEmpty()) {
        throw ProcessingErrorException(
            ProcessingError(
                "Fallback method wasn't found: $methodName",
                sourceMethod,
                Diagnostic.Kind.ERROR
            )
        )
    }

    for (fallbackMethod in fallbackMethods) {
        val reasonParameters = fallbackMethod.parameters
            .filter { parameter ->
                parameter.annotations.any { annotation ->
                    annotation.annotationType.resolve().declaration.qualifiedName?.asString() == "io.koraframework.resilient.fallback.annotation.Fallback.Reason"
                }
            }
        if (reasonParameters.size > 1) {
            throw ProcessingErrorException(
                ProcessingError(
                    "Fallback method can declare only one @Fallback.Reason parameter",
                    fallbackMethod,
                    Diagnostic.Kind.ERROR
                )
            )
        }
        if (fallbackMethod.parameters.size == fallbackArgs.size + reasonParameters.size) {
            return FallbackMeta(methodName, fallbackArgs, reasonParameters.firstOrNull()?.type?.resolve())
        }
    }

    throw ProcessingErrorException(
        ProcessingError(
            "Fallback method doesn't match requested signature: $methodName(${fallbackArgs.joinToString(", ")})",
            sourceMethod,
            Diagnostic.Kind.ERROR
        )
    )
}
