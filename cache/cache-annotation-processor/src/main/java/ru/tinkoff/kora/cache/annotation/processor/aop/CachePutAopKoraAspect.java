package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
        if (MethodUtils.isFlux(method)) {
            throw new ProcessingErrorException("@CachePut can't be applied for types assignable from " + CommonClassNames.flux, method);
        } else if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@CachePut can't be applied for type " + CommonClassNames.publisher, method);
        } else if (MethodUtils.isVoid(method)) {
            throw new ProcessingErrorException("@CachePut can't be applied for type Void", method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheOperation(method, env, aspectContext);
        final CodeBlock body;
        if (MethodUtils.isMono(method)) {
            if (MethodUtils.isMonoVoid(method)) {
                throw new ProcessingErrorException("@CachePut can't be applied for type Void", method);
            }

            body = buildBodyMono(method, operation, superCall);
        } else if (MethodUtils.isFuture(method)) {
            if (MethodUtils.isFutureVoid(method)) {
                throw new ProcessingErrorException("@CachePut can't be applied for type Void", method);
            }

            body = buildBodyFuture(method, operation, superCall);
        } else {
            body = buildBodySync(method, operation, superCall);
        }

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

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);
        var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);
        var isOptional = CommonUtils.isOptional(completionType);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        if (operation.executions().stream().allMatch(e -> e.contract() == CacheOperation.CacheExecution.Contract.SYNC)) {
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
                    builder
                        .add("var $L = ", keyField)
                        .addStatement(cache.cacheKey().code());
                }
            }

            builder.add("return ").add(superMethod)
                .beginControlFlow(".doOnSuccess(_result -> ");

            if (isOptional) {
                builder.beginControlFlow("if(_result.isPresent())");
            } else {
                builder.beginControlFlow("if(_result != null)");
            }

            // cache put
            for (int i = 0; i < operation.executions().size(); i++) {
                var cache = operation.executions().get(i);

                String keyField = "_key" + (i + 1);
                for (int i1 = 0; i1 < i; i1++) {
                    var prevCache = operation.executions().get(i1);
                    if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCache.cacheKey().type())) {
                        keyField = "_key" + (i1 + 1);
                    }
                }

                if (isOptional) {
                    builder.addStatement("$L.put($L, _result.get())", cache.field(), keyField);
                } else {
                    builder.addStatement("$L.put($L, _result)", cache.field(), keyField);
                }
            }
            builder.endControlFlow().endControlFlow(")");
        } else if (operation.executions().size() > 1) {
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
                    builder
                        .add("var $L = ", keyField)
                        .addStatement(cache.cacheKey().code());
                }
            }

            builder.add("return ").add(superMethod);

            if (isOptional) {
                builder.beginControlFlow(".flatMap(_result -> ")
                    .beginControlFlow("if(_result.isPresent())")
                    .add("return $T.merge(\n", CommonClassNames.flux);
            } else {
                builder.add(".flatMap(_result -> $T.merge(\n", CommonClassNames.flux);
            }

            // cache put
            for (int i = 0; i < operation.executions().size(); i++) {
                final CacheOperation.CacheExecution cache = operation.executions().get(i);

                String keyField = "_key" + (i + 1);
                for (int i1 = 0; i1 < i; i1++) {
                    var prevCache = operation.executions().get(i1);
                    if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCache.cacheKey().type())) {
                        keyField = "_key" + (i1 + 1);
                    }
                }

                final String template;
                final String resultField = (isOptional) ? "_result.get()" : "_result";
                if (cache.contract() == CacheOperation.CacheExecution.Contract.ASYNC) {
                    template = (i == operation.executions().size() - 1)
                        ? "$T.fromCompletionStage(() -> $L.putAsync($L, $L))\n"
                        : "$T.fromCompletionStage(() -> $L.putAsync($L, $L)),\n";
                } else {
                    template = (i == operation.executions().size() - 1)
                        ? "$T.just($L.put($L, $L))\n"
                        : "$T.just($L.put($L, $L)),\n";
                }

                builder.add("\t").add(template, CommonClassNames.mono, cache.field(), keyField, resultField);
            }

            if (isOptional) {
                builder.addStatement(").then(Mono.just(_result))")
                    .endControlFlow()
                    .add("\n")
                    .addStatement("return Mono.just(_result)")
                    .endControlFlow(")");
            } else {
                builder.add(").then(Mono.just(_result)));");
            }
        } else {
            builder.add("return ").add(superMethod);
            if (isOptional) {
                builder.add("""
                    .doOnSuccess(_result -> {
                        if(_result.isPresent()) {
                            var _key = $L;
                            $L.put(_key, _result.get());
                        }
                    });
                    """, operation.executions().get(0).cacheKey().code(), operation.executions().get(0).field());
            } else {
                builder.add("""
                    .doOnSuccess(_result -> {
                        if(_result != null) {
                            var _key = $L;
                            $L.put(_key, _result);
                        }
                    });
                    """, operation.executions().get(0).cacheKey().code(), operation.executions().get(0).field());
            }
        }

        return builder.build();
    }

    private CodeBlock buildBodyFuture(ExecutableElement method,
                                      CacheOperation operation,
                                      String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();
        var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);

        // cache super method
        builder.add("return $L\n", superMethod).beginControlFlow(".thenCompose(_value ->");

        var isOptional = CommonUtils.isOptional(completionType);
        builder.add(putCacheBlock(method, operation.executions(), "_value", isOptional));

        builder.addStatement("return $T.completedFuture(_value)", CompletableFuture.class).endControlFlow(")");
        return builder.build();
    }

    CodeBlock putCacheBlock(ExecutableElement method,
                            List<CacheOperation.CacheExecution> executions,
                            String valueField,
                            boolean isValueOptional) {
        var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);
        final CodeBlock.Builder builder = CodeBlock.builder();
        var isOptional = CommonUtils.isOptional(completionType);

        if (isValueOptional) {
            builder.beginControlFlow("if($L.isPresent())", valueField);
        } else {
            builder.beginControlFlow("if($L != null)", valueField);
        }

        // Generate keys
        for (int i = 0; i < executions.size(); i++) {
            var cache = executions.get(i);
            boolean prevKeyMatch = false;
            for (int j = 0; j < i; j++) {
                var prevCachePut = executions.get(j);
                if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCachePut.cacheKey().type())) {
                    prevKeyMatch = true;
                    break;
                }
            }

            if (!prevKeyMatch) {
                var keyField = "_key" + (i + 1);
                builder.addStatement("var $L = $L", keyField, cache.cacheKey().code());
            }
        }

        // Generate puts
        boolean allAreSync = executions.stream().allMatch(o -> o.contract() == CacheOperation.CacheExecution.Contract.SYNC);
        if (allAreSync) {
            for (int j = 0; j < executions.size(); j++) {
                final CacheOperation.CacheExecution cachePrevPut = executions.get(j);
                var putKeyField = "_key" + (j + 1);
                for (int i1 = 0; i1 < executions.size(); i1++) {
                    var prevCachePut = executions.get(i1);
                    if (env.getTypeUtils().isSubtype(cachePrevPut.cacheKey().type(), prevCachePut.cacheKey().type())) {
                        putKeyField = "_key" + (i1 + 1);
                        break;
                    }
                }

                if (isValueOptional) {
                    builder.addStatement("$L.put($L, $L.get())", cachePrevPut.field(), putKeyField, valueField);
                } else {
                    builder.addStatement("$L.put($L, $L)", cachePrevPut.field(), putKeyField, valueField);
                }
            }

            if (isValueOptional || !CommonUtils.isOptional(completionType)) {
                builder.addStatement("return $T.completedFuture($L)", CompletableFuture.class, valueField);
            } else {
                builder.addStatement("return $T.completedFuture($T.of($L))", CompletableFuture.class, Optional.class, valueField);
            }
        } else {
            var codeBlock = CodeBlock.builder();
            for (int j = 0; j < executions.size(); j++) {
                final CacheOperation.CacheExecution cachePrevPut = executions.get(j);
                var putKeyField = "_key" + (j + 1);
                if (j != 0) {
                    codeBlock.add(",\n");
                }

                for (int i1 = 0; i1 < executions.size(); i1++) {
                    var prevCachePut = executions.get(i1);
                    if (env.getTypeUtils().isSubtype(cachePrevPut.cacheKey().type(), prevCachePut.cacheKey().type())) {
                        putKeyField = "_key" + (i1 + 1);
                        break;
                    }
                }

                if (cachePrevPut.contract() == CacheOperation.CacheExecution.Contract.ASYNC) {
                    if (isValueOptional) {
                        codeBlock.add("\t$L.putAsync($L, $L.get()).toCompletableFuture()", cachePrevPut.field(), putKeyField, valueField);
                    } else {
                        codeBlock.add("\t$L.putAsync($L, $L).toCompletableFuture()", cachePrevPut.field(), putKeyField, valueField);
                    }
                } else {
                    if (isValueOptional) {
                        codeBlock.add("\t$T.completedFuture($L.put($L, $L.get()))", CompletableFuture.class, cachePrevPut.field(), putKeyField, valueField);
                    } else {
                        codeBlock.add("\t$T.completedFuture($L.put($L, $L))", CompletableFuture.class, cachePrevPut.field(), putKeyField, valueField);
                    }
                }
            }

            builder.add("return CompletableFuture.allOf(\n").add(codeBlock.build());

            if (isValueOptional || !CommonUtils.isOptional(completionType)) {
                builder.add("\n).thenApply(_ignore -> $L);\n", valueField);
            } else {
                builder.add("\n).thenApply(_ignore -> $T.ofNullable($L));\n", Optional.class, valueField);
            }
        }

        builder.endControlFlow();
        builder.add("\n");
        return builder.build();
    }
}
