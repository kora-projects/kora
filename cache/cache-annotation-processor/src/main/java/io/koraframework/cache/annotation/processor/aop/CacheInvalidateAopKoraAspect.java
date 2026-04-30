package io.koraframework.cache.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.cache.annotation.processor.CacheOperation;
import io.koraframework.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.Set;

import static io.koraframework.cache.annotation.processor.CacheOperationUtils.ANNOTATION_CACHE_INVALIDATE;
import static io.koraframework.cache.annotation.processor.CacheOperationUtils.ANNOTATION_CACHE_INVALIDATES;

public class CacheInvalidateAopKoraAspect extends AbstractAopCacheAspect {

    private final ProcessingEnvironment env;

    public CacheInvalidateAopKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_CACHE_INVALIDATE, ANNOTATION_CACHE_INVALIDATES);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(ANNOTATION_CACHE_INVALIDATE.simpleName()) + CommonClassNames.publisher, method);
        } else if (MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(ANNOTATION_CACHE_INVALIDATE) + method.getReturnType().toString(), method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheOperation(method, env, aspectContext);
        final CodeBlock body = buildBodySync(method, operation, superCall);
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
}
