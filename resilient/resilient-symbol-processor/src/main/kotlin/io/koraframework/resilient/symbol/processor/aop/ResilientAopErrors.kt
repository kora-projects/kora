package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.symbol.KSFunctionDeclaration

internal fun unsupportedReturnTypeError(annotationName: String, function: KSFunctionDeclaration, unsupportedType: Any): String {
    return """
        $annotationName cannot be applied to '${function.parentDeclaration}#${function.simpleName.asString()}' because return type '$unsupportedType' is not supported by this aspect.

        Fix: use a synchronous, suspend, or Flow-returning method for this aspect, or choose an aspect implementation that supports this async type.
    """.trimIndent()
}

internal fun invalidResilientContractError(annotationName: String, function: KSFunctionDeclaration, expectedContract: String): String {
    return """
        $annotationName on '${function.parentDeclaration}#${function.simpleName.asString()}' references an invalid resilient component type.

        The annotation value must extend $expectedContract.
        Fix: point the annotation value to a generated/spec interface or custom component that implements $expectedContract.
    """.trimIndent()
}
