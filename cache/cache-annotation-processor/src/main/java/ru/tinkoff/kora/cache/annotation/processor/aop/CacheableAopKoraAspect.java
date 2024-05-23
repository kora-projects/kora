package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation.CacheExecution;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isFlux(method)) {
            throw new ProcessingErrorException("@Cacheable can't be applied for types assignable from " + CommonClassNames.flux, method);
        } else if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@Cacheable can't be applied for type " + CommonClassNames.publisher, method);
        } else if (MethodUtils.isVoid(method)) {
            throw new ProcessingErrorException("@Cacheable can't be applied for type Void", method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheOperation(method, env, aspectContext);
        final CodeBlock body;
        if (MethodUtils.isMono(method)) {
            if (MethodUtils.isMonoVoid(method)) {
                throw new ProcessingErrorException("@Cacheable can't be applied for type Void", method);
            }

            body = buildBodyMono(method, operation, superCall);
        } else if (MethodUtils.isFuture(method)) {
            if (MethodUtils.isFutureVoid(method)) {
                throw new ProcessingErrorException("@Cacheable can't be applied for type Void", method);
            }

            body = buildBodyFutureNew(method, operation, superCall);
        } else {
            body = buildBodySync(method, operation, superCall);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock.Builder getCacheSyncBlock(ExecutableElement method, CacheOperation operation) {
        final boolean isOptional = MethodUtils.isOptional(method);

        String keyField = "_key1";
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache get
        for (int i = 0; i < operation.executions().size(); i++) {
            final CacheExecution cache = operation.executions().get(i);
            final String prefix = (i == 0)
                ? "var _value = "
                : "_value = ";

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

            builder.add(prefix).add(cache.field()).add(".get($L);\n", keyField);

            builder.beginControlFlow("if(_value != null)");
            // put value from cache into prev level caches
            for (int j = 0; j < i; j++) {
                final CacheExecution cachePrevPut = operation.executions().get(j);

                var putKeyField = "_key" + (j + 1);
                for (int i1 = 0; i1 < i; i1++) {
                    var prevCachePut = operation.executions().get(i1);
                    if (env.getTypeUtils().isSubtype(cachePrevPut.cacheKey().type(), prevCachePut.cacheKey().type())) {
                        putKeyField = "_key" + (i1 + 1);
                    }
                }

                builder.add(cachePrevPut.field()).add(".put($L, _value);\n", putKeyField);
            }

            if (isOptional) {
                builder.add("return $T.of(_value);", Optional.class);
            } else if (MethodUtils.isFuture(method)) {
                var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);
                if (CommonUtils.isOptional(completionType)) {
                    builder.add("return $T.completedFuture($T.of(_value));", CompletableFuture.class, Optional.class);
                } else {
                    builder.add("return $T.completedFuture(_value);", CompletableFuture.class);
                }
            } else if (MethodUtils.isMono(method)) {
                var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);
                if (CommonUtils.isOptional(completionType)) {
                    builder.add("return $T.just($T.of(_value));", CommonClassNames.mono, Optional.class);
                } else {
                    builder.add("return $T.just(_value);", CommonClassNames.mono);
                }
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

        final boolean isOptional = MethodUtils.isOptional(method);
        if (operation.executions().size() == 1) {
            final String keyField = "_key";
            final CodeBlock keyBlock = CodeBlock.builder()
                .add("var $L = ", keyField)
                .addStatement(operation.executions().get(0).cacheKey().code())
                .build();
            if (isOptional) {
                return CodeBlock.builder()
                    .add(keyBlock)
                    .add("return $T.ofNullable($L.computeIfAbsent($L, _k -> $L.orElse(null)));", Optional.class, operation.executions().get(0).field(), keyField, superMethod)
                    .build();
            } else {
                return CodeBlock.builder()
                    .add(keyBlock)
                    .add("return $L.computeIfAbsent($L, _k -> $L);", operation.executions().get(0).field(), keyField, superMethod)
                    .build();
            }
        }

        var builder = getCacheSyncBlock(method, operation);

        // cache super method
        builder.add("var _result = ").add(superMethod).add(";\n");

        // cache put
        final boolean isPrimitive = method.getReturnType() instanceof PrimitiveType;
        if (isOptional) {
            builder.beginControlFlow("_result.ifPresent(_v ->");
        } else if (!isPrimitive) {
            builder.beginControlFlow("if(_result != null)");
        }

        for (int i = 0; i < operation.executions().size(); i++) {
            var cache = operation.executions().get(i);

            var putKeyField = "_key" + (i + 1);
            for (int i1 = 0; i1 < i; i1++) {
                var prevCachePut = operation.executions().get(i1);
                if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCachePut.cacheKey().type())) {
                    putKeyField = "_key" + (i1 + 1);
                }
            }

            if (isOptional) {
                builder.add(cache.field()).add(".put($L, _v);\n", putKeyField);
            } else {
                builder.add(cache.field()).add(".put($L, _result);\n", putKeyField);
            }
        }

        if (isOptional) {
            builder.endControlFlow(")");
        } else if (!isPrimitive) {
            builder.endControlFlow();
        }

        builder.add("return _result;");
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

        if (operation.executions().stream().allMatch(e -> e.contract() == CacheExecution.Contract.SYNC)) {
            builder.beginControlFlow("return $T.defer(() -> ", CommonClassNames.mono);
            builder.add(getCacheSyncBlock(method, operation).build());

            builder.add("return $L", superMethod)
                .beginControlFlow(".doOnSuccess(_r ->");

            var codeBlock = CodeBlock.builder();
            for (int j = 0; j < operation.executions().size(); j++) {
                final CacheExecution cache = operation.executions().get(j);
                String keyField = "_key" + (j + 1);
                for (int i1 = 0; i1 < j; i1++) {
                    var prevCache = operation.executions().get(i1);
                    if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCache.cacheKey().type())) {
                        keyField = "_key" + (i1 + 1);
                        break;
                    }
                }

                var putExec = operation.executions().get(j);
                if (isOptional) {
                    codeBlock.addStatement("$L.put($L, _r.get())", putExec.field(), keyField);
                } else {
                    codeBlock.addStatement("$L.put($L, _r)", putExec.field(), keyField);
                }
            }

            if (isOptional) {
                builder.beginControlFlow("if (_r.isPresent())")
                    .add(codeBlock.build())
                    .endControlFlow();
            } else {
                builder.beginControlFlow("if (_r != null)")
                    .add(codeBlock.build())
                    .endControlFlow();
            }

            builder
                .endControlFlow(")")
                .endControlFlow(")");
        } else {
            for (int i = 0; i < operation.executions().size(); i++) {
                final CacheExecution cache = operation.executions().get(i);
                boolean prevKeyMatch = false;
                String keyField;
                for (int i1 = 0; i1 < i; i1++) {
                    var prevCache = operation.executions().get(i1);
                    if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCache.cacheKey().type())) {
                        prevKeyMatch = true;
                    }
                }

                if (!prevKeyMatch) {
                    keyField = "_key" + (i + 1);
                    builder
                        .add("var $L = ", keyField)
                        .addStatement(cache.cacheKey().code());
                }
            }

            // cache get
            for (int i = 0; i < operation.executions().size(); i++) {
                final CacheExecution cache = operation.executions().get(i);
                final String prefix = (i == 0)
                    ? "var _value = "
                    : "_value = _value.switchIfEmpty(";

                String keyField = "_key" + (i + 1);
                for (int i1 = 0; i1 < i; i1++) {
                    var prevCache = operation.executions().get(i1);
                    if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCache.cacheKey().type())) {
                        keyField = "_key" + (i1 + 1);
                    }
                }

                if (cache.contract() == CacheExecution.Contract.ASYNC) {
                    builder.add(prefix)
                        .add("$T.fromCompletionStage(() -> $L", CommonClassNames.mono, cache.field())
                        .add(".getAsync($L))", keyField);
                } else {
                    builder.add(prefix)
                        .add("$T.fromCallable(() -> $L", CommonClassNames.mono, cache.field())
                        .add(".get($L))", keyField);
                }

                // put value from cache into prev level caches
                if (i > 1) {
                    builder.add("\n").add("""
                            .doOnSuccess(_fromCache -> {
                                if(_fromCache != null) {
                                    $T.merge(
                        """, CommonClassNames.flux);

                    for (int j = 0; j < i; j++) {
                        final CacheExecution prevCache = operation.executions().get(j);

                        String putKeyField = "_key" + (j + 1);
                        for (int i1 = 0; i1 < i; i1++) {
                            var putPrevCache = operation.executions().get(i1);
                            if (env.getTypeUtils().isSubtype(prevCache.cacheKey().type(), putPrevCache.cacheKey().type())) {
                                putKeyField = "_key" + (i1 + 1);
                            }
                        }

                        final String template;
                        if (cache.contract() == CacheExecution.Contract.ASYNC) {
                            template = (j == i - 1)
                                ? "$T.fromCompletionStage(() -> $L.putAsync($L, _fromCache))\n"
                                : "$T.fromCompletionStage(() -> $L.putAsync($L, _fromCache)),\n";
                        } else {
                            template = (j == i - 1)
                                ? "$T.just($L.put($L, _fromCache))\n"
                                : "$T.just($L.put($L, _fromCache)),\n";
                        }

                        builder.add("\t\t\t\t").add(template, CommonClassNames.mono, prevCache.field(), putKeyField);
                    }


                    builder.add("\t\t).then().block();\n}}));\n\n");
                } else if (i == 1) {
                    builder.add("\n\t")
                        .add(String.format("""
                            .doOnSuccess(_fromCache -> {
                                    if(_fromCache != null) {
                                        %s.put(_key1, _fromCache);
                                    }
                            }));
                            """, operation.executions().get(0).field()))
                        .add("\n");
                } else {
                    builder.add(";\n");
                }
            }

            // cache super method
            if (isOptional) {
                builder.add("return _value.map($T::of).switchIfEmpty(", Optional.class).add(superMethod);
            } else {
                builder.add("return _value.switchIfEmpty(").add(superMethod);
            }

            // cache put
            if (operation.executions().size() > 1) {
                if (isOptional) {
                    builder.beginControlFlow(".flatMap(_result -> ")
                        .beginControlFlow("if(_result.isPresent())")
                        .add("return $T.merge(\n", CommonClassNames.flux);
                } else {
                    builder.add(".flatMap(_result -> $T.merge(\n", CommonClassNames.flux);
                }

                for (int i = 0; i < operation.executions().size(); i++) {
                    final CacheExecution cache = operation.executions().get(i);
                    String putKeyField = "_key" + (i + 1);
                    for (int i1 = 0; i1 < i; i1++) {
                        var putPrevCache = operation.executions().get(i1);
                        if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), putPrevCache.cacheKey().type())) {
                            putKeyField = "_key" + (i1 + 1);
                        }
                    }

                    final String template;
                    final String resultField = (isOptional) ? "_result.get()" : "_result";
                    if (cache.contract() == CacheExecution.Contract.ASYNC) {
                        template = (i == operation.executions().size() - 1)
                            ? "$T.fromCompletionStage(() -> $L.putAsync($L, $L))\n"
                            : "$T.fromCompletionStage(() -> $L.putAsync($L, $L)),\n";
                    } else {
                        template = (i == operation.executions().size() - 1)
                            ? "$T.just($L.put($L, $L))\n"
                            : "$T.just($L.put($L, $L)),\n";
                    }

                    builder.add("\t").add(template, CommonClassNames.mono, cache.field(), putKeyField, resultField);
                }

                if (isOptional) {
                    builder.addStatement(").then(Mono.just(_result))")
                        .endControlFlow()
                        .add("\n")
                        .addStatement("return Mono.just(_result)")
                        .endControlFlow("))");
                } else {
                    builder.add(").then(Mono.just(_result))));");
                }
            } else {
                final CacheExecution execution = operation.executions().get(0);
                if (isOptional) {
                    builder.add("""
                        .doOnSuccess(_result -> {
                            if(_result.isPresent()) {
                                var _key = $L;
                                $L.put(_key, _result.get());
                            }
                        }));
                        """, execution.cacheKey().code(), execution.field());
                } else {
                    builder.add("""
                        .doOnSuccess(_result -> {
                            if(_result != null) {
                                var _key = $L;
                                $L.put(_key, _result);
                            }
                        }));
                        """, execution.cacheKey().code(), execution.field());
                }
            }
        }

        return builder.build();
    }

    CodeBlock putCacheBlock(ExecutableElement method,
                            List<CacheOperation.CacheExecution> executions,
                            String valueField,
                            boolean isValueOptional) {
        var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);
        final CodeBlock.Builder builder = CodeBlock.builder();

        if (isValueOptional) {
            builder.beginControlFlow("if($L.isPresent())", valueField);
        } else {
            builder.beginControlFlow("if($L != null)", valueField);
        }

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

    private CodeBlock buildBodyFutureNew(ExecutableElement method,
                                         CacheOperation operation,
                                         String superCall) {
        final String superMethod = getSuperMethod(method, superCall);
        final CodeBlock.Builder builder = CodeBlock.builder();

        String keyField = "_key1";
        int wraps = 0;

        // cache get
        for (int i = 0; i < operation.executions().size(); i++) {
            final CacheExecution cache = operation.executions().get(i);
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
                builder.addStatement("var $L = $L", keyField, cache.cacheKey().code());
            }

            int index = i + 1;
            var valueField = "_value" + index;
            if (cache.contract() == CacheExecution.Contract.SYNC) {
                builder.add("var $L = $L.get($L);\n", valueField, cache.field(), keyField);
            } else {
                wraps++;
                builder.beginControlFlow("return $L.getAsync($L).thenCompose($L ->", cache.field(), keyField, valueField);
            }

            // put value from cache into prev level caches
            var putExecutions = operation.executions().stream().limit(i).toList();
            builder.add(putCacheBlock(method, putExecutions, valueField, false));
        }

        var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);
        var isOptional = CommonUtils.isOptional(completionType);
        builder.beginControlFlow("return $L.thenCompose(_r ->", superMethod);
        builder.add(putCacheBlock(method, operation.executions(), "_r", isOptional));
        builder.addStatement("return $T.completedFuture(_r)", CompletableFuture.class);
        builder.endControlFlow(")");

        for (int w = 0; w < wraps; w++) {
            builder.endControlFlow(")");
        }

        return builder.build();
    }
}
