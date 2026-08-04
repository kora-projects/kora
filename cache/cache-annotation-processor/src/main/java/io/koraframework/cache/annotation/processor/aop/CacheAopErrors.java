package io.koraframework.cache.annotation.processor.aop;

import javax.lang.model.element.ExecutableElement;

final class CacheAopErrors {
    private CacheAopErrors() {}

    static String unsupportedReturnTypeError(String annotationName, ExecutableElement method, Object returnType) {
        return """
            %s cannot be applied to '%s#%s()' because return type '%s' is not supported by the Java cache AOP aspect.

            Fix: use a synchronous method return type supported by cache AOP. Cache get/put methods must return a value; invalidate methods may return void.
            """.formatted(annotationName, method.getEnclosingElement(), method.getSimpleName(), returnType).trim();
    }

    static String voidReturnTypeError(String annotationName, ExecutableElement method) {
        return """
            %s cannot be applied to '%s#%s()' because the method returns void.

            Fix: cache get/put methods must return the value that should be read from or written to cache.
            """.formatted(annotationName, method.getEnclosingElement(), method.getSimpleName()).trim();
    }
}
