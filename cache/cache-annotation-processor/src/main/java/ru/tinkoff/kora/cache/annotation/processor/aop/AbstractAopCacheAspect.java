package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.List;

import static com.squareup.javapoet.CodeBlock.joining;

abstract class AbstractAopCacheAspect implements KoraAspect {

    private static final ClassName KEY_CACHE = ClassName.get("ru.tinkoff.kora.cache", "CacheKey");

    ClassName getCacheKey(CacheOperation operation) {
        return KEY_CACHE;
    }

    List<String> getCacheFields(CacheOperation operation, ProcessingEnvironment env, AspectContext aspectContext) {
        final List<String> cacheFields = new ArrayList<>();
        for (var cacheImpl : operation.cacheImplementations()) {
            var cacheElement = env.getElementUtils().getTypeElement(cacheImpl);
            var fieldCache = aspectContext.fieldFactory().constructorParam(cacheElement.asType(), List.of());
            cacheFields.add(fieldCache);
        }

        return cacheFields;
    }

    String getKeyRecordParameters(CacheOperation operation, ExecutableElement method) {
        return String.join(", ", operation.getParametersNames(method));
    }

    String getSuperMethod(ExecutableElement method, String superCall) {
        return method.getParameters().stream()
            .map(p -> CodeBlock.of("$L", p))
            .collect(joining(", ", superCall + "(", ")")).toString();
    }
}
