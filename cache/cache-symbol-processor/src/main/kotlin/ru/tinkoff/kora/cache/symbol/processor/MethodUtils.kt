package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono

@KspExperimental
class MethodUtils {

    companion object {
        fun KSFunctionDeclaration.getParameters(parameterNames: List<String>): List<KSValueParameter> {
            return if (parameterNames.isEmpty()) {
                parameters.stream()
                    .filter { it.isParameterSupported(parameterNames) }
                    .toList()
            } else {
                val methodParameters = mutableListOf<KSValueParameter>()
                for (parameter in parameterNames) {
                    val arg = parameters.asSequence()
                        .filter { it.name!!.getShortName() == parameter }
                        .firstOrNull()

                    if (arg != null) {
                        methodParameters.add(arg)
                    } else {
                        throw IllegalArgumentException("Specified CacheKey parameter '$parameter' is not present in method signature: $origin")
                    }
                }
                methodParameters
            }
        }

        fun KSFunctionDeclaration.getReturnValueCanonicalName(): String {
            return if (isMono())
                returnType!!.resolve().arguments.first().type!!.resolve().toClassName().canonicalName
            else {
                returnType!!.resolve().toClassName().canonicalName
            }
        }

        private fun KSValueParameter.isParameterSupported(parameterNames: List<String>): Boolean = parameterNames.isEmpty() || parameterNames.contains(name!!.getShortName())
    }
}
