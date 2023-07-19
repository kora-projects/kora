package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.Cacheables;
import ru.tinkoff.kora.cache.annotation.processor.CacheMeta;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Set;

import static ru.tinkoff.kora.cache.annotation.processor.CacheOperationManager.getCacheOperation;

public class CacheableAopKoraAspect extends AbstractAopCacheAspect {

    private final ProcessingEnvironment env;

    public CacheableAopKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Cacheable.class.getCanonicalName(), Cacheables.class.getCanonicalName());
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        final CacheOperation operation = getCacheOperation(method, env);
        final CacheMirrors cacheMirrors = getCacheMirrors(operation, method, env);

        final List<String> cacheFields = getCacheFields(operation, cacheMirrors, aspectContext);
        final CodeBlock body = MethodUtils.isMono(method)
            ? buildBodyMono(method, operation, superCall, cacheFields)
            : buildBodySync(method, operation, superCall, cacheFields);

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall,
                                    List<String> cacheFields) {
        final String recordParameters = getKeyRecordParameters(operation, method);
        final String superMethod = getSuperMethod(method, superCall);

        final StringBuilder builder = new StringBuilder();

        // cache get
        for (int i = 0; i < cacheFields.size(); i++) {
            final String cache = cacheFields.get(i);
            final String prefix = (i == 0)
                ? "var _value = "
                : "_value = ";

            builder.append(prefix)
                .append(cache).append(".get(_key);\n")
                .append("if(_value != null) {\n");

            // put value from cache into prev level caches
            for (int j = 0; j < i; j++) {
                final String cachePrevPut = cacheFields.get(j);
                builder.append("\t").append(cachePrevPut).append(".put(_key, _value);\n");
            }

            builder.append("""
                        return _value;
                     }
                    """)
                .append("\n");
        }

        // cache super method
        builder.append("_value = ").append(superMethod).append(";\n");

        // cache put
        for (int i = 0; i < cacheFields.size(); i++) {
            final String cache = cacheFields.get(i);
            builder.append(cache).append(".put(_key, _value);\n");
        }
        builder.append("return _value;");

        return CodeBlock.builder()
            .add("""
                    var _key = new $L($L);
                    """,
                operation.key().simpleName(), recordParameters)
            .add(builder.toString())
            .build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    CacheOperation operation,
                                    String superCall,
                                    List<String> cacheFields) {
        final String recordParameters = getKeyRecordParameters(operation, method);
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final StringBuilder builder = new StringBuilder();

        // cache get
        for (int i = 0; i < cacheFields.size(); i++) {
            final String cache = cacheFields.get(i);
            final String prefix = (i == 0)
                ? "var _value = "
                : "_value = _value.switchIfEmpty(";

            final String getPart = ".getAsync(_key)";
            builder.append(prefix)
                .append(cache)
                .append(getPart);

            // put value from cache into prev level caches
            if (i > 1) {
                builder.append("\n").append("""
                        .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .doOnSuccess(_fromCache -> {
                            if(_fromCache != null) {
                                reactor.core.publisher.Flux.merge(java.util.List.of(
                    """);

                for (int j = 0; j < i; j++) {
                    final String prevCache = cacheFields.get(j);
                    final String suffix = (j == i - 1)
                        ? ".putAsync(_key, _fromCache)\n"
                        : ".putAsync(_key, _fromCache),\n";
                    builder.append("\t\t\t\t").append(prevCache).append(suffix);
                }

                builder.append("\t\t)).then().block();\n}}));\n\n");
            } else if (i == 1) {
                builder.append("\n\t")
                    .append(String.format("""
                        .doOnSuccess(_fromCache -> {
                                if(_fromCache != null) {
                                    %s.put(_key, _fromCache);
                                }
                        }));
                        """, cacheFields.get(0)))
                    .append("\n");
            } else {
                builder.append(";\n");
            }
        }

        // cache super method
        builder.append("return _value.switchIfEmpty(").append(superMethod);

        // cache put
        if (cacheFields.size() > 1) {
            builder.append(".flatMap(_result -> reactor.core.publisher.Flux.merge(java.util.List.of(\n");
            for (int i = 0; i < cacheFields.size(); i++) {
                final String cache = cacheFields.get(i);
                final String suffix = (i == cacheFields.size() - 1)
                    ? ".putAsync(_key, _result)\n"
                    : ".putAsync(_key, _result),\n";
                builder.append("\t").append(cache).append(suffix);
            }
            builder.append(")).then(Mono.just(_result))));");
        } else {
            builder.append(".doOnSuccess(_result -> ").append(cacheFields.get(0)).append(".put(_key, _result)));\n");
        }

        return CodeBlock.builder()
            .add("""
                    var _key = new $L($L);
                    """,
                operation.key().simpleName(), recordParameters)
            .add(builder.toString())
            .build();
    }
}
