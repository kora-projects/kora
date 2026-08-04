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
                """
                @Fallback method reference '$fallbackSignature' has invalid syntax.

                Fix: use method reference syntax 'methodName()' or 'methodName(arg1, arg2)'.
                Example: @Fallback(method = "fallback(value)")
                """.trimIndent(),
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
                    """
                    @Fallback method reference '$fallbackSignature' uses unknown source arguments: $illegalArgs.

                    Available arguments on '${sourceMethod.simpleName.asString()}': $sourceArgs.
                    Fix: use only parameters declared by the annotated method, or remove the arguments from the fallback reference.
                    """.trimIndent(),
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
                """
                @Fallback method '$methodName' was not found in '${sourceMethod.parentDeclaration}'.

                Fix: declare a fallback method with this name in the same class as '${sourceMethod.simpleName.asString()}', or update @Fallback(method = "...") to the existing method name.
                """.trimIndent(),
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
                    """
                    @Fallback method '${fallbackMethod.simpleName.asString()}' declares more than one @Fallback.Reason parameter.

                    Fix: keep at most one @Fallback.Reason parameter. It receives the exception that triggered fallback.
                    """.trimIndent(),
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
            """
            @Fallback method '$methodName' does not match requested signature '$methodName(${fallbackArgs.joinToString(", ")})'.

            Fix: make the fallback method accept exactly the referenced source arguments, plus optionally one @Fallback.Reason parameter.
            """.trimIndent(),
            sourceMethod,
            Diagnostic.Kind.ERROR
        )
    )
}
