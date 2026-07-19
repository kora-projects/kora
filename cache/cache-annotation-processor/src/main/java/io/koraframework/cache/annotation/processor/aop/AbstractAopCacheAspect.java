package io.koraframework.cache.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.aop.annotation.processor.KoraAspect;
import io.koraframework.cache.annotation.processor.CacheOperation;

import javax.lang.model.element.ExecutableElement;
import java.util.List;

import static com.palantir.javapoet.CodeBlock.joining;

abstract class AbstractAopCacheAspect implements KoraAspect {

    private static final ClassName CACHE_ASYNC_EXECUTOR = ClassName.get("io.koraframework.cache", "CacheAsyncExecutor");

    String getSuperMethod(ExecutableElement method, String superCall) {
        return method.getParameters().stream()
            .map(p -> CodeBlock.of("$L", p))
            .collect(joining(", ", superCall + "(", ")")).toString();
    }

    String getExecutorField(CacheOperation operation, AspectContext aspectContext) {
        if (operation.executions().stream().noneMatch(this::isAsync)) {
            return null;
        }

        return aspectContext.fieldFactory().constructorParam(CACHE_ASYNC_EXECUTOR, List.of());
    }

    boolean isAsync(CacheOperation.CacheExecution cache) {
        return cache.async() && !cache.caffeine();
    }

    CodeBlock cachePut(String executorField, CacheOperation.CacheExecution cache, String key, String value) {
        if (isAsync(cache)) {
            return CodeBlock.of("$L.execute(() -> $L.put($L, $L));\n", executorField, cache.field(), key, value);
        }

        return CodeBlock.of("$L.put($L, $L);\n", cache.field(), key, value);
    }

    CodeBlock cacheInvalidate(String executorField, CacheOperation.CacheExecution cache, String key) {
        if (isAsync(cache)) {
            return CodeBlock.of("$L.execute(() -> $L.invalidate($L));\n", executorField, cache.field(), key);
        }

        return CodeBlock.of("$L.invalidate($L);\n", cache.field(), key);
    }

    CodeBlock cacheInvalidateAll(String executorField, CacheOperation.CacheExecution cache) {
        if (isAsync(cache)) {
            return CodeBlock.of("$L.execute(() -> $L.invalidateAll());\n", executorField, cache.field());
        }

        return CodeBlock.of("$L.invalidateAll();\n", cache.field());
    }
}
