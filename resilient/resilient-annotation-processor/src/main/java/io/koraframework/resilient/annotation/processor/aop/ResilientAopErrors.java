package io.koraframework.resilient.annotation.processor.aop;

import javax.lang.model.element.ExecutableElement;

final class ResilientAopErrors {
    private ResilientAopErrors() {}

    static String unsupportedReturnTypeError(String annotationName, ExecutableElement method, Object unsupportedType) {
        return """
            %s cannot be applied to '%s#%s()' because return type '%s' is not supported by this aspect.

            Fix: use a synchronous, CompletionStage-compatible, or supported reactive method for this aspect, depending on the Java AP capabilities.
            """.formatted(annotationName, method.getEnclosingElement(), method.getSimpleName(), unsupportedType).trim();
    }

    static String invalidResilientContractError(String annotationName, ExecutableElement method, String expectedContract) {
        return """
            %s on '%s#%s()' references an invalid resilient component type.

            The annotation value must extend %s.
            Fix: point the annotation value to a generated/spec interface or custom component that implements %s.
            """.formatted(annotationName, method.getEnclosingElement(), method.getSimpleName(), expectedContract, expectedContract).trim();
    }
}
