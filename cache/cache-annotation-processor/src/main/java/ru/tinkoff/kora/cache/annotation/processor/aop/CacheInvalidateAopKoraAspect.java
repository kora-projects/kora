package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.Set;

public class CacheInvalidateAopKoraAspect extends AbstractAopCacheAspect {

    private static final ClassName ANNOTATION_CACHE_INVALIDATE = ClassName.get("ru.tinkoff.kora.cache.annotation", "CacheInvalidate");
    private static final ClassName ANNOTATION_CACHE_INVALIDATES = ClassName.get("ru.tinkoff.kora.cache.annotation", "CacheInvalidates");

    private final ProcessingEnvironment env;

    public CacheInvalidateAopKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_CACHE_INVALIDATE.canonicalName(), ANNOTATION_CACHE_INVALIDATES.canonicalName());
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_CACHE_INVALIDATE, ANNOTATION_CACHE_INVALIDATES);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isFlux(method)) {
            throw new ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from " + CommonClassNames.flux, method);
        } else if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@CacheInvalidate can't be applied for type " + CommonClassNames.publisher, method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheOperation(method, env, aspectContext);

        final CodeBlock body;
        if (operation.type() == CacheOperation.Type.EVICT_ALL) {
            body = buildBodySyncAll(method, operation, superCall);
        } else {
            body = buildBodySync(method, operation, superCall);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock getSyncBlock(ExecutableElement method, CacheOperation operation) {
        // cache variables
        var builder = CodeBlock.builder();

        // create keys
        for (int i = 0; i < operation.executions().size(); i++) {
            var cache = operation.executions().get(i);
            boolean prevKeyMatch = false;
            for (int j = 0; j < i; j++) {
                var prevCachePut = operation.executions().get(j);
                if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCachePut.cacheKey().type())) {
                    prevKeyMatch = true;
                    break;
                }
            }

            if (!prevKeyMatch) {
                var keyField = "_key" + (i + 1);
                builder.add("var $L = ", keyField).addStatement(cache.cacheKey().code());
            }
        }

        // cache invalidate
        for (int i = 0; i < operation.executions().size(); i++) {
            var cache = operation.executions().get(i);

            String keyField = "_key" + (i + 1);
            for (int i1 = 0; i1 < i; i1++) {
                var prevCache = operation.executions().get(i1);
                if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCache.cacheKey().type())) {
                    keyField = "_key" + (i1 + 1);
                }
            }

            builder.addStatement("$L.invalidate($L)", cache.field(), keyField);
        }

        return builder.build();
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        var builder = CodeBlock.builder();

        // cache super method
        if (MethodUtils.isVoid(method)) {
            builder.addStatement(superMethod);
        } else {
            builder.add("var value = ").addStatement(superMethod);
        }

        builder.add(getSyncBlock(method, operation));

        if (!MethodUtils.isVoid(method)) {
            builder.addStatement("return value");
        }

        return builder.build();
    }

    private CodeBlock buildBodySyncAll(ExecutableElement method,
                                       CacheOperation operation,
                                       String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final StringBuilder builder = new StringBuilder();

        // cache super method
        if (MethodUtils.isVoid(method)) {
            builder.append(superMethod).append(";\n");
        } else {
            builder.append("var _value = ").append(superMethod).append(";\n");
        }

        // cache invalidate
        for (var cache : operation.executions()) {
            builder.append(cache.field()).append(".invalidateAll();\n");
        }

        if (!MethodUtils.isVoid(method)) {
            builder.append("return _value;");
        }

        return CodeBlock.builder()
            .add(builder.toString())
            .build();
    }
}
