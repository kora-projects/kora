package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.PrimitiveType;
import java.util.List;
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
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isFlux(method)) {
            throw new ProcessingErrorException("@CachePut can't be applied for types assignable from " + CommonClassNames.flux, method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheOperation(method, env, aspectContext);
        final CodeBlock body;
        if (MethodUtils.isMono(method)) {
            body = buildBodyMono(method, operation, superCall);
        } else if (MethodUtils.isFuture(method)) {
            body = buildBodyFuture(method, operation, superCall);
        } else {
            body = buildBodySync(method, operation, superCall);
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache super method
        builder.add("var _value = ").add(superMethod).add(";\n");

        final boolean isOptional = MethodUtils.isOptional(method);
        final boolean isPrimitive = method.getReturnType() instanceof PrimitiveType;
        if (isOptional) {
            builder.beginControlFlow("_value.ifPresent(_v ->");
        } else if (!isPrimitive) {
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

            if (isOptional) {
                builder.add("$L.put($L, _v);\n", cache.field(), putKeyField);
            } else {
                builder.add("$L.put($L, _value);\n", cache.field(), putKeyField);
            }
        }

        if (isOptional) {
            builder.endControlFlow(")");
        } else if (!isPrimitive) {
            builder.endControlFlow();
        }

        builder.add("return _value;");

        return builder.build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache super method

        if (operation.executions().size() > 1) {

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

            builder.add("return ")
                .add(superMethod)
                .add(".flatMap(_result -> $T.merge($T.of(\n", CommonClassNames.flux, List.class);

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
                        ? "$T.fromCompletionStage(() -> $L.putAsync($L, _result))\n"
                        : "$T.fromCompletionStage(() -> $L.putAsync($L, _result)),\n";
                } else {
                    template = (i == operation.executions().size() - 1)
                        ? "$T.justOrEmpty($L.put($L, _result))\n"
                        : "$T.justOrEmpty($L.put($L, _result)),\n";
                }

                builder.add("\t").add(template, CommonClassNames.mono, cache.field(), keyField);
            }
            builder.add(")).then(Mono.just(_result)));");
        } else {
            builder.add("return ").add(superMethod);
            builder.add("""
                .doOnSuccess(_result -> {
                    if(_result != null) {
                        var _key = $L;
                        $L.put(_key, _result);
                    }
                });
                """, operation.executions().get(0).cacheKey().code(), operation.executions().get(0).field());
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
        builder.add("return $L\n", superMethod)
            .indent()
            .beginControlFlow(".thenCompose(_value ->");

        builder.beginControlFlow("if(_value == null)")
            .addStatement("return $T.completedFuture(null)", CompletableFuture.class)
            .endControlFlow();

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

        builder.add("return $T.allOf(\n", CompletableFuture.class);

        // cache put
        for (int i = 0; i < operation.executions().size(); i++) {
            if (i != 0) {
                builder.add(",\n");
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
                builder.add("\t$L.putAsync($L, _value).toCompletableFuture()", cache.field(), putKeyField);
            } else {
                builder.add("\t$T.completedFuture($L.put($L, _value))", CompletableFuture.class, cache.field(), putKeyField);
            }
        }

        builder
            .add("\n).thenApply(_v -> _value);\n");

        return builder
            .endControlFlow(")")
            .unindent()
            .build();
    }
}
