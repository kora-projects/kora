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
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
        if (MethodUtils.isMono(method)) {
            if (operation.type() == CacheOperation.Type.EVICT_ALL) {
                body = buildBodyMonoAll(method, operation, superCall);
            } else {
                body = buildBodyMono(method, operation, superCall);
            }
        } else if (MethodUtils.isFuture(method)) {
            if (operation.type() == CacheOperation.Type.EVICT_ALL) {
                body = buildBodyFutureAll(method, operation, superCall);
            } else {
                body = buildBodyFuture(method, operation, superCall);
            }
        } else {
            if (operation.type() == CacheOperation.Type.EVICT_ALL) {
                body = buildBodySyncAll(method, operation, superCall);
            } else {
                body = buildBodySync(method, operation, superCall);
            }
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

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);
        var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);
        var isOptional = CommonUtils.isOptional(completionType);

        // cache variables
        var builder = CodeBlock.builder();

        if (operation.executions().stream().allMatch(e -> e.contract() == CacheOperation.CacheExecution.Contract.SYNC)) {
            // call super
            builder.add("return ").add(superMethod);
            builder.beginControlFlow(".doOnSuccess(_result -> ");

            if (!MethodUtils.isMonoVoid(method)) {
                if (isOptional) {
                    builder.beginControlFlow("if(_result.isPresent())");
                } else {
                    builder.beginControlFlow("if(_result != null)");
                }
            }
            builder.add(getSyncBlock(method, operation));
            if (!MethodUtils.isMonoVoid(method)) {
                builder.endControlFlow();
            }

            builder.endControlFlow(")");
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
                    builder.addStatement("var $L = $L", keyField, cache.cacheKey().code());
                }
            }

            // call super
            builder.add("return ").add(superMethod);

            if (MethodUtils.isMonoVoid(method)) {
                builder.add(".then(\n").indent();
            } else {
                builder.add(".flatMap(_result -> \n").indent();
            }

            builder.add("$T.merge(\n", CommonClassNames.flux);

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
                if (cache.contract() == CacheOperation.CacheExecution.Contract.ASYNC) {
                    template = (i == operation.executions().size() - 1)
                        ? "$T.fromCompletionStage(() -> $L.invalidateAsync($L))\n"
                        : "$T.fromCompletionStage(() -> $L.invalidateAsync($L)),\n";
                } else {
                    template = (i == operation.executions().size() - 1)
                        ? "$T.fromRunnable(() -> $L.invalidate($L))\n"
                        : "$T.fromRunnable(() -> $L.invalidate($L)),\n";
                }

                builder.add("\t").add(template, CommonClassNames.mono, cache.field(), keyField);
            }

            if (MethodUtils.isMonoVoid(method)) {
                builder.add(").then()\n").unindent().addStatement(")");
            } else {
                builder.add(").then($T.just(_result))\n", CommonClassNames.mono).unindent().addStatement(")");
            }
        } else {
            // call super
            builder.add("return ").add(superMethod);
            builder.add(".doOnSuccess(_result -> $L.invalidate($L));", operation.executions().get(0).field(), operation.executions().get(0).cacheKey().code());
        }

        return builder.build();
    }

    private CodeBlock buildBodyMonoAll(ExecutableElement method,
                                       CacheOperation operation,
                                       String superCall) {
        final String superMethod = getSuperMethod(method, superCall);
        var completionType = ((DeclaredType) method.getReturnType()).getTypeArguments().get(0);
        var isOptional = CommonUtils.isOptional(completionType);

        // cache variables
        var builder = CodeBlock.builder();

        // cache super method
        builder.add("return ").add(superMethod);

        if (operation.executions().stream().allMatch(e -> e.contract() == CacheOperation.CacheExecution.Contract.SYNC)) {
            builder.beginControlFlow(".doOnSuccess(_result -> ");

            if (!MethodUtils.isMonoVoid(method)) {
                if (isOptional) {
                    builder.beginControlFlow("if(_result.isPresent())");
                } else {
                    builder.beginControlFlow("if(_result != null)");
                }
            }

            for (var cache : operation.executions()) {
                builder.addStatement("$L.invalidateAll()", cache.field());
            }

            if (!MethodUtils.isMonoVoid(method)) {
                builder.endControlFlow();
            }

            builder.endControlFlow(")");
            return builder.build();
        } else if (operation.executions().size() > 1) {
            if (MethodUtils.isMonoVoid(method)) {
                builder.add(".then(\n").indent();
            } else {
                builder.add(".flatMap(_result -> \n").indent();
            }

            builder.add("$T.merge(\n", CommonClassNames.flux);

            // cache put
            for (int i = 0; i < operation.executions().size(); i++) {
                final CacheOperation.CacheExecution cache = operation.executions().get(i);

                final String template;
                if (cache.contract() == CacheOperation.CacheExecution.Contract.ASYNC) {
                    template = (i == operation.executions().size() - 1)
                        ? "$T.fromCompletionStage(() -> $L.invalidateAllAsync())\n"
                        : "$T.fromCompletionStage(() -> $L.invalidateAllAsync()),\n";
                } else {
                    template = (i == operation.executions().size() - 1)
                        ? "$T.fromRunnable(() -> $L.invalidateAll())\n"
                        : "$T.fromRunnable(() -> $L.invalidateAll()),\n";
                }

                builder.add("\t").add(template, CommonClassNames.mono, cache.field());
            }

            if (MethodUtils.isMonoVoid(method)) {
                builder.add(").then()\n").unindent().addStatement(")");
            } else {
                builder.add(").then($T.just(_result))\n", CommonClassNames.mono).unindent().addStatement(")");
            }
        } else {
            builder.add(".doOnSuccess(_result -> ")
                .add(operation.executions().get(0).field())
                .add(".invalidateAll());\n");
        }

        return builder.build();
    }

    private CodeBlock buildBodyFuture(ExecutableElement method,
                                      CacheOperation operation,
                                      String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache super method
        builder.add("return $L\n", superMethod);

        if (operation.executions().stream().allMatch(e -> e.contract() == CacheOperation.CacheExecution.Contract.SYNC)) {
            builder.beginControlFlow(".thenApply(_result -> ");
            builder.add(getSyncBlock(method, operation));
            builder.addStatement("return _result").endControlFlow(")");
            return builder.build();
        } else {
            builder.indent().beginControlFlow(".thenCompose(_value ->");

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
            var allBuilder = CodeBlock.builder();
            allBuilder.add("return $T.allOf(\n", CompletableFuture.class);
            for (int i = 0; i < operation.executions().size(); i++) {
                if (i != 0 && operation.executions().get(i - 1).contract() == CacheOperation.CacheExecution.Contract.ASYNC) {
                    allBuilder.add(",\n");
                }

                var cache = operation.executions().get(i);
                var putKeyField = "_key" + (i + 1);
                for (int i1 = 0; i1 < i; i1++) {
                    var prevCachePut = operation.executions().get(i1);
                    if (env.getTypeUtils().isSubtype(cache.cacheKey().type(), prevCachePut.cacheKey().type())) {
                        putKeyField = "_key" + (i1 + 1);
                    }
                }

                if (cache.contract() == CacheOperation.CacheExecution.Contract.ASYNC) {
                    allBuilder.add("\t$L.invalidateAsync($L).toCompletableFuture()", cache.field(), putKeyField);
                } else {
                    builder.addStatement("$L.invalidate($L)", cache.field(), putKeyField);
                }
            }

            builder
                .add(allBuilder.build())
                .add("\n).thenApply(_v -> _value);\n");

            return builder
                .endControlFlow(")")
                .unindent()
                .build();
        }
    }

    private CodeBlock buildBodyFutureAll(ExecutableElement method,
                                         CacheOperation operation,
                                         String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache super method
        builder.add("return $L\n", superMethod);

        if (operation.executions().stream().allMatch(e -> e.contract() == CacheOperation.CacheExecution.Contract.SYNC)) {
            builder.beginControlFlow(".thenApply(_result -> ");
            for (var cache : operation.executions()) {
                builder.addStatement("$L.invalidateAll()", cache.field());
            }
            builder.addStatement("return _result").endControlFlow(")");
            return builder.build();
        } else {
            builder
                .indent()
                .beginControlFlow(".thenCompose(_value ->");


            // cache put
            var allBuilder = CodeBlock.builder();
            allBuilder.add("return $T.allOf(\n", CompletableFuture.class);
            for (int i = 0; i < operation.executions().size(); i++) {
                if (i != 0 && operation.executions().get(i - 1).contract() == CacheOperation.CacheExecution.Contract.ASYNC) {
                    allBuilder.add(",\n");
                }

                var cache = operation.executions().get(i);
                if (cache.contract() == CacheOperation.CacheExecution.Contract.ASYNC) {
                    allBuilder.add("\t$L.invalidateAllAsync().toCompletableFuture()", cache.field());
                } else {
                    builder.addStatement("$L.invalidateAll()", cache.field());
                }
            }

            builder
                .add(allBuilder.build())
                .add("\n).thenApply(_v -> _value);\n");

            return builder
                .endControlFlow(")")
                .unindent()
                .build();
        }
    }
}
