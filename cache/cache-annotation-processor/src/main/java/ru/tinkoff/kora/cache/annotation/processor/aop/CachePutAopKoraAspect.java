package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.CachePuts;
import ru.tinkoff.kora.cache.annotation.processor.CacheMeta;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Set;

import static ru.tinkoff.kora.cache.annotation.processor.CacheOperationManager.getCacheOperation;

public class CachePutAopKoraAspect extends AbstractAopCacheAspect {

    private final ProcessingEnvironment env;

    public CachePutAopKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(CachePut.class.getCanonicalName(), CachePuts.class.getCanonicalName());
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        final CacheOperation operation = getCacheOperation(method, env);
        final AbstractAopCacheAspect.CacheMirrors cacheMirrors = getCacheMirrors(operation, method, env);

        final List<String> cacheFields = getCacheFields(operation, cacheMirrors, aspectContext);
        final CodeBlock body = MethodUtils.isMono(method, env)
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

        // cache variables
        final StringBuilder builder = new StringBuilder();

        // cache super method
        builder.append("var _value = ").append(superMethod).append(";\n");

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

        // cache super method
        builder.append("return ").append(superMethod);

        if (cacheFields.size() > 1) {
            builder.append(".flatMap(_result -> reactor.core.publisher.Flux.merge(java.util.List.of(\n");

            // cache put
            for (int i = 0; i < cacheFields.size(); i++) {
                final String cache = cacheFields.get(i);
                final String suffix = (i == cacheFields.size() - 1)
                    ? ".putAsync(_key, _result)\n"
                    : ".putAsync(_key, _result),\n";
                builder.append("\t").append(cache).append(suffix);
            }
            builder.append(")).then(Mono.just(_result)));");
        } else {
            builder.append(".doOnSuccess(_result -> ").append(cacheFields.get(0)).append(".put(_key, _result));\n");
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
