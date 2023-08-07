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
        if (MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@CachePut can't be applied for types assignable from " + Future.class, method);
        } else if (MethodUtils.isFlux(method)) {
            throw new ProcessingErrorException("@CachePut can't be applied for types assignable from " + Flux.class, method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheMeta(method);
        final List<String> cacheFields = getCacheFields(operation, env, aspectContext);

        final CodeBlock body = MethodUtils.isMono(method)
            ? buildBodyMono(method, operation, cacheFields, superCall)
            : buildBodySync(method, operation, cacheFields, superCall);

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    CacheOperation operation,
                                    List<String> cacheFields,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache super method
        builder.add("var _value = ").add(superMethod).add(";\n");

        if (operation.parameters().size() == 1) {
            builder.add("""
                    var _key = $L;
                    """,
                operation.parameters().get(0));
        } else {
            final String recordParameters = getKeyRecordParameters(operation, method);
            builder.add("""
                    var _key = $T.of($L);
                    """,
                getCacheKey(operation), recordParameters);
        }

        // cache put
        for (var cache : cacheFields) {
            builder.add(cache).add(".put(_key, _value);\n");
        }
        builder.add("return _value;");

        return builder.build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    CacheOperation operation,
                                    List<String> cacheFields,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache super method

        if (cacheFields.size() > 1) {
            if (operation.parameters().size() == 1) {
                builder.add("""
                    var _key = $L;
                    """, operation.parameters().get(0));
            } else {
                final String recordParameters = getKeyRecordParameters(operation, method);
                builder.add("""
                    var _key = $T.of($L);
                    """, getCacheKey(operation), recordParameters);
            }

            builder.add("return ")
                .add(superMethod)
                .add(".flatMap(_result -> $T.merge($T.of(\n", Flux.class, List.class);

            // cache put
            for (int i = 0; i < cacheFields.size(); i++) {
                final String cache = cacheFields.get(i);
                final String suffix = (i == cacheFields.size() - 1)
                    ? ".putAsync(_key, _result)\n"
                    : ".putAsync(_key, _result),\n";
                builder.add("\t").add(cache).add(suffix);
            }
            builder.add(")).then(Mono.just(_result)));");
        } else {
            builder.add("return ").add(superMethod);
            if (operation.parameters().size() == 1) {
                builder.add("""
                    .doOnSuccess(_result -> {
                        if(_result != null) {
                            $L.put($L, _result);
                        }
                    });
                    """, cacheFields.get(0), operation.parameters().get(0));
            } else {
                final String recordParameters = getKeyRecordParameters(operation, method);
                builder.add("""
                    .doOnSuccess(_result -> {
                        if(_result != null) {
                            $L.put($T.of($L), _result);
                        }
                    });
                    """, cacheFields.get(0), getCacheKey(operation), recordParameters);
            }
        }

        return builder.build();
    }
}
