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

import static io.koraframework.cache.annotation.processor.CacheOperationUtils.ANNOTATION_CACHE_INVALIDATE_ALL;
import static io.koraframework.cache.annotation.processor.CacheOperationUtils.ANNOTATION_CACHE_INVALIDATE_ALLS;

public class CacheInvalidateAllAopKoraAspect extends AbstractAopCacheAspect {

    private final ProcessingEnvironment env;

    public CacheInvalidateAllAopKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_CACHE_INVALIDATE_ALL, ANNOTATION_CACHE_INVALIDATE_ALLS);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(ANNOTATION_CACHE_INVALIDATE_ALL.simpleName()) + CommonClassNames.publisher, method);
        } else if (MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@%s can't be applied for type ".formatted(ANNOTATION_CACHE_INVALIDATE_ALL) + method.getReturnType().toString(), method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheOperation(method, env, aspectContext);
        final CodeBlock body = buildBodySyncAll(method, operation, superCall, aspectContext);
        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySyncAll(ExecutableElement method,
                                       CacheOperation operation,
                                       String superCall,
                                       AspectContext aspectContext) {
        final String superMethod = getSuperMethod(method, superCall);
        final String executorField = getExecutorField(operation, aspectContext);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache super method
        if (MethodUtils.isVoid(method)) {
            builder.addStatement(superMethod);
        } else {
            builder.add("var _value = ").addStatement(superMethod);
        }

        // cache invalidate
        for (var cache : operation.executions()) {
            builder.add(cacheInvalidateAll(executorField, cache));
        }

        if (!MethodUtils.isVoid(method)) {
            builder.addStatement("return _value");
        }

        return builder.build();
    }
}
