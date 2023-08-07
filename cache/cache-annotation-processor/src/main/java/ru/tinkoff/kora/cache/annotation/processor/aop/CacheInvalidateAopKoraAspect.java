package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import reactor.core.publisher.Flux;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

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
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from " + Future.class, method);
        } else if (MethodUtils.isFlux(method)) {
            throw new ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from " + Flux.class, method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheMeta(method);
        final List<String> cacheFields = getCacheFields(operation, env, aspectContext);

        final CodeBlock body;
        if (MethodUtils.isMono(method)) {
            if (operation.type() == CacheOperation.Type.EVICT_ALL) {
                body = buildBodyMonoAll(method, operation, cacheFields, superCall);
            } else {
                body = buildBodyMono(method, operation, cacheFields, superCall);
            }
        } else {
            if (operation.type() == CacheOperation.Type.EVICT_ALL) {
                body = buildBodySyncAll(method, operation, cacheFields, superCall);
            } else {
                body = buildBodySync(method, operation, cacheFields, superCall);
            }
        }

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    CacheOperation operation,
                                    List<String> cacheFields,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final StringBuilder builder = new StringBuilder();

        // cache super method
        if (MethodUtils.isVoid(method)) {
            builder.append(superMethod).append(";\n");
        } else {
            builder.append("var value = ").append(superMethod).append(";\n");
        }

        // cache invalidate
        for (final String cache : cacheFields) {
            builder.append(cache).append(".invalidate(_key);\n");
        }

        if (MethodUtils.isVoid(method)) {
            builder.append("return;");
        } else {
            builder.append("return value;");
        }

        if (operation.parameters().size() == 1) {
            return CodeBlock.builder()
                .add("""
                        var _key = $L;
                        """,
                    operation.parameters().get(0))
                .add(builder.toString())
                .build();
        } else {
            final String recordParameters = getKeyRecordParameters(operation, method);
            return CodeBlock.builder()
                .add("""
                        var _key = $T.of($L);
                        """,
                    getCacheKey(operation), recordParameters)
                .add(builder.toString())
                .build();
        }
    }

    private CodeBlock buildBodySyncAll(ExecutableElement method,
                                       CacheOperation operation,
                                       List<String> cacheFields,
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
        for (final String cache : cacheFields) {
            builder.append(cache).append(".invalidateAll();\n");
        }

        if (MethodUtils.isVoid(method)) {
            builder.append("return;");
        } else {
            builder.append("return _value;");
        }

        return CodeBlock.builder()
            .add(builder.toString())
            .build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    CacheOperation operation,
                                    List<String> cacheFields,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final StringBuilder builder = new StringBuilder();

        // cache super method
        builder.append("return ").append(superMethod);

        if (cacheFields.size() > 1) {
            builder.append(".publishOn(reactor.core.scheduler.Schedulers.boundedElastic()).doOnSuccess(_result -> reactor.core.publisher.Flux.merge(java.util.List.of(\n");

            // cache put
            for (int i = 0; i < cacheFields.size(); i++) {
                final String cache = cacheFields.get(i);
                final String suffix = (i == cacheFields.size() - 1)
                    ? ".invalidateAsync(_key)\n"
                    : ".invalidateAsync(_key),\n";
                builder.append("\t").append(cache).append(suffix);
            }
            builder.append(")).then().block());");
        } else {
            builder.append(".doOnSuccess(_result -> ").append(cacheFields.get(0)).append(".invalidate(_key));\n");
        }

        if (operation.parameters().size() == 1) {
            return CodeBlock.builder()
                .add("""
                        var _key = $L;
                        """,
                    operation.parameters().get(0))
                .add(builder.toString())
                .build();
        } else {
            final String recordParameters = getKeyRecordParameters(operation, method);
            return CodeBlock.builder()
                .add("""
                        var _key = $T.of($L);
                        """,
                    getCacheKey(operation), recordParameters)
                .add(builder.toString())
                .build();
        }
    }

    private CodeBlock buildBodyMonoAll(ExecutableElement method,
                                       CacheOperation operation,
                                       List<String> cacheFields,
                                       String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final StringBuilder builder = new StringBuilder();

        // cache super method
        builder.append("return ").append(superMethod);

        if (cacheFields.size() > 1) {
            builder.append(".publishOn(reactor.core.scheduler.Schedulers.boundedElastic()).doOnSuccess(_result -> reactor.core.publisher.Flux.merge(java.util.List.of(\n");

            // cache put
            for (int i = 0; i < cacheFields.size(); i++) {
                final String cache = cacheFields.get(i);
                final String suffix = (i == cacheFields.size() - 1)
                    ? ".invalidateAllAsync()\n"
                    : ".invalidateAllAsync(),\n";
                builder.append("\t").append(cache).append(suffix);
            }
            builder.append(")).then().block());");
        } else {
            builder.append(".doOnSuccess(_result -> ").append(cacheFields.get(0)).append(".invalidateAll());\n");
        }

        return CodeBlock.builder()
            .add(builder.toString())
            .build();
    }
}
