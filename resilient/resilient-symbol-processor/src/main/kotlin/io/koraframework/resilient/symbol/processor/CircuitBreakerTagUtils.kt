package io.koraframework.resilient.symbol.processor

import com.squareup.kotlinpoet.ClassName

object CircuitBreakerTagUtils {
    const val CONFIG_PATH_ROOT = "resilient.circuitbreaker"
    const val TELEMETRY_CONFIG_PATH = "resilient.circuitbreaker.telemetry"
    const val TAG_PACKAGE = "io.koraframework.resilient.circuitbreaker.generated"
    const val CONFIG_PATH_FIELD = "CONFIG_PATH"

    fun tagName(configPath: String): ClassName {
        return ClassName(TAG_PACKAGE, generatedName(configPath))
    }

    fun moduleName(configPath: String): ClassName {
        return ClassName(TAG_PACKAGE, generatedName(configPath) + "Module")
    }

    fun factoryMethodName(configPath: String): String {
        val generatedName = generatedName(configPath)
        return generatedName.replaceFirstChar { it.lowercaseChar() } + "FactoryModule"
    }

    fun isReservedPath(configPath: String): Boolean {
        return configPath == CONFIG_PATH_ROOT || configPath == TELEMETRY_CONFIG_PATH
    }

    private fun generatedName(configPath: String): String {
        val builder = StringBuilder("CircuitBreaker")
        var capitalizeNext = true
        for (c in configPath) {
            if (c.isLetterOrDigit()) {
                if (capitalizeNext) {
                    builder.append(c.uppercaseChar())
                    capitalizeNext = false
                } else {
                    builder.append(c)
                }
            } else {
                capitalizeNext = true
            }
        }
        return builder.toString()
    }
}
