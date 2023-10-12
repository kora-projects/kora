package ru.tinkoff.kora.cache.annotation.processor;

import com.squareup.javapoet.CodeBlock;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.List;

public record CacheOperation(Type type, List<CacheExecution> executions, Origin origin) {

    public record CacheExecution(String field, TypeElement type, DeclaredType superType, Contract contract, CacheKey cacheKey) {

        public enum Contract {
            SYNC,
            ASYNC
        }
    }

    public record CacheKey(DeclaredType type, CodeBlock code) {}

    public record Origin(String className, String methodName) {

        @Override
        public String toString() {
            return "[class=" + className + ", method=" + methodName + ']';
        }
    }

    public enum Type {
        GET,
        PUT,
        EVICT,
        EVICT_ALL
    }
}
