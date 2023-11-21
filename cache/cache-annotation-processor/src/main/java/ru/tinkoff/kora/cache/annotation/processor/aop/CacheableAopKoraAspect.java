package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation.CacheExecution;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
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
            if(MethodUtils.isMonoVoid(method)) {
                throw new ProcessingErrorException("@Cacheable can't be applied for type Void", method);
            }

            body = buildBodyMono(method, operation, superCall);
        } else if (MethodUtils.isFuture(method)) {
            if(MethodUtils.isFutureVoid(method)) {
                throw new ProcessingErrorException("@Cacheable can't be applied for type Void", method);
            }

            body = buildBodyFuture(method, operation, superCall);
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
                builder.add("return $T.completedFuture(_value);", CompletableFuture.class);
            } else if (MethodUtils.isMono(method)) {
                builder.add("return $T.just(_value);", CommonClassNames.mono);
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
                codeBlock.addStatement("$L.put($L, _r)", putExec.field(), keyField);
            }

            builder.beginControlFlow("if (_r != null)")
                .add(codeBlock.build())
                .endControlFlow();

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
                        .add("$T.justOrEmpty($L", CommonClassNames.mono, cache.field())
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
                                ? "$T.justOrEmpty($L.put($L, _fromCache))\n"
                                : "$T.justOrEmpty($L.put($L, _fromCache)),\n";
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
            builder.add("return _value.switchIfEmpty(").add(superMethod);

            // cache put
            if (operation.executions().size() > 1) {
                builder.add(".flatMap(_result -> $T.merge(\n", CommonClassNames.flux);
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
                    if (cache.contract() == CacheExecution.Contract.ASYNC) {
                        template = (i == operation.executions().size() - 1)
                            ? "$T.fromCompletionStage(() -> $L.putAsync($L, _result))\n"
                            : "$T.fromCompletionStage(() -> $L.putAsync($L, _result)),\n";
                    } else {
                        template = (i == operation.executions().size() - 1)
                            ? "$T.justOrEmpty($L.put($L, _result))\n"
                            : "$T.justOrEmpty($L.put($L, _result)),\n";
                    }

                    builder.add("\t").add(template, CommonClassNames.mono, cache.field(), putKeyField);
                }
                builder.add(").then(Mono.just(_result))));");
            } else {
                var cacheExecution = operation.executions().get(0);
                builder.add("""
                    .doOnSuccess(_result -> {
                        if(_result != null) {
                            var _key = $L;
                            $L.put(_key, _result);
                        }
                    }));
                    """, cacheExecution.cacheKey().code(), cacheExecution.field());
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

        if (operation.executions().stream().allMatch(e -> e.contract() == CacheExecution.Contract.SYNC)) {
            builder.add(getCacheSyncBlock(method, operation).build());
            builder.add("return $L", superMethod)
                .beginControlFlow(".thenApply(_r ->");

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
                codeBlock.addStatement("$L.put($L, _r)", putExec.field(), keyField);
            }

            builder.beginControlFlow("if (_r != null)")
                .add(codeBlock.build())
                .endControlFlow();

            builder
                .addStatement("return _r")
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
            for (int i = 0; i < operation.executions().size() + 1; i++) {
                if (i == 0) {
                    final CacheExecution cache = operation.executions().get(i);
                    if (cache.contract() == CacheExecution.Contract.ASYNC) {
                        builder.add("return $L.getAsync($L))", cache.field(), "_key1")
                            .indent();
                    } else {
                        builder.add("return $T.completedFuture($L.get($L))", CompletableFuture.class, cache.field(), "_key1")
                            .indent();
                    }
                }

                // put value from cache into prev level caches
                if (i > 0) {
                    builder.beginControlFlow(".thenCompose(_r ->");
                    if (i == 1) {
                        builder.add("""
                            if (_r != null) {
                                return CompletableFuture.completedFuture(_r);
                            }
                            """);
                    } else {
                        var codeBlock = CodeBlock.builder();
                        for (int j = 0; j < i - 1; j++) {
                            if (j != 0) {
                                codeBlock.add(",\n");
                            }

                            var prevExec = operation.executions().get(j);
                            String keyField = "_key" + (j + 1);
                            for (int i1 = 0; i1 < j; i1++) {
                                var prevCache = operation.executions().get(i1);
                                if (env.getTypeUtils().isSubtype(prevExec.cacheKey().type(), prevCache.cacheKey().type())) {
                                    keyField = "_key" + (i1 + 1);
                                    break;
                                }
                            }

                            var putExec = operation.executions().get(j);
                            if (putExec.contract() == CacheExecution.Contract.ASYNC) {
                                codeBlock.add("\t$L.putAsync($L, _r).toCompletableFuture()", putExec.field(), keyField);
                            } else {
                                codeBlock.add("\t$T.completedFuture($L.put($L, _r))", CompletableFuture.class, putExec.field(), keyField);
                            }
                        }

                        builder.beginControlFlow("if (_r != null)")
                            .add("return CompletableFuture.allOf(\n")
                            .add(codeBlock.build())
                            .add("\n).thenApply(_v -> _r);\n")
                            .endControlFlow();
                    }

                    if (i != operation.executions().size()) {
                        final CacheExecution cache = operation.executions().get(i);
                        String keyField = "_key" + (i + 1);
                        for (int i1 = 0; i1 < i; i1++) {
                            var prevCache = operation.executions().get(i1);
                            if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCache.cacheKey().type())) {
                                keyField = "_key" + (i1 + 1);
                            }
                        }

                        if (cache.contract() == CacheExecution.Contract.ASYNC) {
                            builder.addStatement("return $L.getAsync($L)", cache.field(), keyField);
                        } else {
                            builder.addStatement("return $T.completedFuture($L.get($L)", CompletableFuture.class, cache.field(), keyField);
                        }
                        builder.endControlFlow().add(")");
                    }
                } else {
                    builder.add("\n");
                }
            }

            builder.addStatement("return $L", superMethod)
                .endControlFlow().add(")")
                .beginControlFlow(".thenCompose(_r ->");

            var codeBlock = CodeBlock.builder();
            for (int j = 0; j < operation.executions().size(); j++) {
                if (j != 0) {
                    codeBlock.add(",\n");
                }

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
                if (putExec.contract() == CacheExecution.Contract.ASYNC) {
                    codeBlock.add("\t$L.putAsync($L, _r).toCompletableFuture()", putExec.field(), keyField);
                } else {
                    codeBlock.add("\t$T.completedFuture($L.put($L, _r))", CompletableFuture.class, putExec.field(), keyField);
                }
            }

            builder.beginControlFlow("if (_r != null)")
                .add("return CompletableFuture.allOf(\n")
                .add(codeBlock.build())
                .add("\n).thenApply(_v -> _r);\n")
                .endControlFlow();

            builder
                .addStatement("return $T.completedFuture(null)", CompletableFuture.class)
                .endControlFlow(")");
        }

        return builder.build();
    }
}
