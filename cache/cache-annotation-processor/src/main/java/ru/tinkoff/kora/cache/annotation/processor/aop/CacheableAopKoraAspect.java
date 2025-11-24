package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation.CacheExecution;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.PrimitiveType;
import java.util.Optional;
import java.util.Set;

public class CacheableAopKoraAspect extends AbstractAopCacheAspect {

    private static final ClassName ANNOTATION_CACHEABLE = ClassName.get("ru.tinkoff.kora.cache.annotation", "Cacheable");
    private static final ClassName ANNOTATION_CACHEABLES = ClassName.get("ru.tinkoff.kora.cache.annotation", "Cacheables");

    private final ProcessingEnvironment env;

    public CacheableAopKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_CACHEABLE.canonicalName(), ANNOTATION_CACHEABLES.canonicalName());
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_CACHEABLE, ANNOTATION_CACHEABLES);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method) || MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@Cacheable can't be applied for async methods", method);
        }
        if (MethodUtils.isVoid(method)) {
            throw new ProcessingErrorException("@Cacheable can't be applied for type Void", method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheOperation(method, env, aspectContext);
        final CodeBlock body = buildBodySync(method, operation, superCall);

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock.Builder getCacheSyncBlock(ExecutableElement method, CacheOperation operation) {
        final boolean isOptionalMethod = MethodUtils.isOptional(method);
        final boolean isOptionalMethodSkip = isOptionalMethod && operation.executions().stream().noneMatch(this::isCacheOptional);
        final boolean isOptionalCacheAny = operation.executions().stream().anyMatch(this::isCacheOptional);

        String keyField = "_key1";
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache get
        for (int i = 0; i < operation.executions().size(); i++) {
            final CacheExecution cache = operation.executions().get(i);
            final boolean isOptionalCache = isCacheOptional(cache);
            final String prefix = (i == 0)
                ? "var _value"
                : "_value";

            boolean prevKeyMatch = false;
            for (int i1 = 0; i1 < i; i1++) {
                var prevCache = operation.executions().get(i1);
                if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCache.cacheKey().type())) {
                    keyField = "_key" + (i1 + 1);
                    prevKeyMatch = true;
                    break;
                }
            }

            if (!prevKeyMatch) {
                keyField = "_key" + (i + 1);
                builder
                    .add("var $L = ", keyField)
                    .addStatement(cache.cacheKey().code());
            }

            if (isOptionalMethod && isOptionalCacheAny && !isOptionalCache) {
                builder.add("$L = $T.ofNullable($L.get($L));\n", prefix, Optional.class, cache.field(), keyField);
            } else if (!isOptionalMethod && isOptionalCache) {
                builder.add("var _$L_optional = $L.get($L);\n", cache.field(), cache.field(), keyField);
                builder.add("$L = _$L_optional == null ? null : _$L_optional.orElse(null);\n", prefix, cache.field(), cache.field());
            } else {
                builder.add("$L = $L.get($L);\n", prefix, cache.field(), keyField);
            }

            if (isOptionalMethod && isOptionalCacheAny && !isOptionalCache) {
                builder.beginControlFlow("if(_value.isPresent())");
            } else {
                builder.beginControlFlow("if(_value != null)");
            }

            // put value from cache into prev level caches
            for (int j = 0; j < i; j++) {
                final CacheExecution cachePrevPut = operation.executions().get(j);
                final boolean isOptionalPrevCache = isCacheOptional(cachePrevPut);

                var putKeyField = "_key" + (j + 1);
                for (int i1 = 0; i1 < i; i1++) {
                    var prevCachePut = operation.executions().get(i1);
                    if (env.getTypeUtils().isSubtype(cachePrevPut.cacheKey().type(), prevCachePut.cacheKey().type())) {
                        putKeyField = "_key" + (i1 + 1);
                    }
                }

                if (isOptionalMethod && isOptionalCacheAny && !isOptionalPrevCache) {
                    builder.beginControlFlow("_value.ifPresent(_v ->");
                    builder.add("$L.put($L, _v);\n", cachePrevPut.field(), putKeyField);
                    builder.endControlFlow(")");
                } else if (!isOptionalMethod && isOptionalPrevCache) {
                    builder.add("$L.put($L, Optional.ofNullable(_value));\n", cachePrevPut.field(), putKeyField);
                } else {
                    builder.add("$L.put($L, _value);\n", cachePrevPut.field(), putKeyField);
                }
            }

            if (isOptionalMethodSkip) {
                builder.add("return $T.of(_value);", Optional.class);
            } else {
                builder.add("return _value;");
            }

            builder.add("\n");
            builder.endControlFlow();
            builder.add("\n");
        }

        return builder;
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        final boolean isOptionalMethod = MethodUtils.isOptional(method);
        final boolean isOptionalCacheAny = operation.executions().stream().anyMatch(this::isCacheOptional);
        final boolean isOptionalMethodSkip = isOptionalMethod && operation.executions().stream().noneMatch(this::isCacheOptional);

        if (operation.executions().size() == 1) {
            final CacheExecution cache = operation.executions().get(0);
            final String keyField = "_key";
            final CodeBlock keyBlock = CodeBlock.builder()
                .add("var $L = ", keyField)
                .addStatement(cache.cacheKey().code())
                .build();
            if (isOptionalMethodSkip) {
                return CodeBlock.builder()
                    .add(keyBlock)
                    .add("return $T.ofNullable($L.computeIfAbsent($L, _k -> $L.orElse(null)));", Optional.class, cache.field(), keyField, superMethod)
                    .build();
            } else if (!isOptionalMethod && isCacheOptional(cache)) {
                return CodeBlock.builder()
                    .add(keyBlock)
                    .add("return $L.computeIfAbsent($L, _k -> $T.ofNullable($L)).orElse(null);", cache.field(), keyField, Optional.class, superMethod)
                    .build();
            } else {
                return CodeBlock.builder()
                    .add(keyBlock)
                    .add("return $L.computeIfAbsent($L, _k -> $L);", cache.field(), keyField, superMethod)
                    .build();
            }
        }

        var builder = getCacheSyncBlock(method, operation);

        // cache super method
        builder.add("var _result = ").add(superMethod).add(";\n");

        // cache put
        final boolean isPrimitive = method.getReturnType() instanceof PrimitiveType;
        if (isOptionalMethodSkip) {
            builder.beginControlFlow("_result.ifPresent(_v ->");
        } else if (!isPrimitive && isOptionalMethod) {
            builder.beginControlFlow("if(_result != null)");
        }

        for (int i = 0; i < operation.executions().size(); i++) {
            var cache = operation.executions().get(i);
            final boolean isOptionalCache = isCacheOptional(cache);

            var putKeyField = "_key" + (i + 1);
            for (int i1 = 0; i1 < i; i1++) {
                var prevCachePut = operation.executions().get(i1);
                if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCachePut.cacheKey().type())) {
                    putKeyField = "_key" + (i1 + 1);
                }
            }

            if (isOptionalMethodSkip) {
                builder.add("$L.put($L, _v);\n", cache.field(), putKeyField);
            } else if (isOptionalMethod && isOptionalCacheAny && !isOptionalCache) {
                builder.beginControlFlow("_result.ifPresent(_v ->");
                builder.add("$L.put($L, _v);\n", cache.field(), putKeyField);
                builder.endControlFlow(")");
            } else if (!isOptionalMethod && isOptionalCache) {
                builder.add("$L.put($L, Optional.ofNullable(_result));\n", cache.field(), putKeyField);
            } else {
                builder.add("$L.put($L, _result);\n", cache.field(), putKeyField);
            }
        }

        if (isOptionalMethodSkip) {
            builder.endControlFlow(")");
        } else if (!isPrimitive && isOptionalMethod) {
            builder.endControlFlow();
        }

        builder.add("return _result;");
        return builder.build();
    }

    private boolean isCacheOptional(CacheExecution execution) {
        return CommonUtils.isOptional(execution.superType().getTypeArguments().get(1));
    }
}
