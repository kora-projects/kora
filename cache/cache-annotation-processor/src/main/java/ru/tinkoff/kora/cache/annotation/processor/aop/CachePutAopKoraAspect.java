package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.PrimitiveType;
import java.util.Optional;
import java.util.Set;

public class CachePutAopKoraAspect extends AbstractAopCacheAspect {

    private static final ClassName ANNOTATION_CACHE_PUT = ClassName.get("ru.tinkoff.kora.cache.annotation", "CachePut");
    private static final ClassName ANNOTATION_CACHE_PUTS = ClassName.get("ru.tinkoff.kora.cache.annotation", "CachePuts");

    private final ProcessingEnvironment env;

    public CachePutAopKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_CACHE_PUT.canonicalName(), ANNOTATION_CACHE_PUTS.canonicalName());
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_CACHE_PUT, ANNOTATION_CACHE_PUTS);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method) || MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@CachePut can't be applied for async methods", method);
        }
        if (MethodUtils.isVoid(method)) {
            throw new ProcessingErrorException("@CachePut can't be applied for type Void", method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheOperation(method, env, aspectContext);
        final CodeBlock body = buildBodySync(method, operation, superCall);

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock getSyncBlock(ExecutableElement method, CacheOperation operation) {
        final CodeBlock.Builder builder = CodeBlock.builder();

        final boolean isOptionalMethod = MethodUtils.isOptional(method);
        final boolean isOptionalMethodSkip = isOptionalMethod && operation.executions().stream().noneMatch(this::isCacheOptional);
        final boolean isOptionalCacheAny = operation.executions().stream().anyMatch(this::isCacheOptional);

        final boolean isPrimitive = method.getReturnType() instanceof PrimitiveType;
        if (isOptionalMethodSkip) {
            builder.beginControlFlow("_value.ifPresent(_v ->");
        } else if (!isPrimitive && isOptionalMethod) {
            builder.beginControlFlow("if(_value != null)");
        }

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

        // cache put
        for (int i = 0; i < operation.executions().size(); i++) {
            var cache = operation.executions().get(i);

            var putKeyField = "_key" + (i + 1);
            for (int i1 = 0; i1 < i; i1++) {
                var prevCachePut = operation.executions().get(i1);
                if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCachePut.cacheKey().type())) {
                    putKeyField = "_key" + (i1 + 1);
                }
            }

            if (isOptionalMethodSkip) {
                builder.add("$L.put($L, _v);\n", cache.field(), putKeyField);
            } else if (isOptionalMethod && !isCacheOptional(cache)) {
                builder.beginControlFlow("_value.ifPresent(_v ->");
                builder.add("$L.put($L, _v);\n", cache.field(), putKeyField);
                builder.endControlFlow(")");
            } else if (!isOptionalMethod && isCacheOptional(cache)) {
                builder.add("$L.put($L, $T.ofNullable(_value));\n", cache.field(), putKeyField, Optional.class);
            } else {
                builder.add("$L.put($L, _value);\n", cache.field(), putKeyField);
            }
        }

        if (isOptionalMethodSkip) {
            builder.endControlFlow(")");
        } else if (!isPrimitive && isOptionalMethod) {
            builder.endControlFlow();
        }

        return builder.add("return _value;").build();
    }

    private boolean isCacheOptional(CacheOperation.CacheExecution execution) {
        return CommonUtils.isOptional(execution.superType().getTypeArguments().get(1));
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache super method
        builder.add("var _value = $L;\n", superMethod);
        builder.add(getSyncBlock(method, operation));
        return builder.build();
    }

}
