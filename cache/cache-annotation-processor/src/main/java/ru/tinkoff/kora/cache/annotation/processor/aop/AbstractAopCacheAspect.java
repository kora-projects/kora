package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.CacheManager;
import ru.tinkoff.kora.cache.annotation.processor.CacheMeta;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.common.Tag;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.squareup.javapoet.CodeBlock.joining;

abstract class AbstractAopCacheAspect implements KoraAspect {

    record CacheMirrors(TypeMirror manager, TypeMirror cache) {}

    CacheMirrors getCacheMirrors(CacheOperation operation, ExecutableElement method, ProcessingEnvironment env) {
        final TypeElement keyElement = env.getElementUtils().getTypeElement(operation.key().canonicalName());
        if (keyElement == null) {
            throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.NOTE, "Cache Key is not yet generated, will try next round...", method));
        }

        final TypeElement valueElement = env.getElementUtils().getTypeElement(operation.value().canonicalName());
        if (valueElement == null) {
            throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.NOTE, "Cache Return type is not yet known, will try next round...", method));
        }

        final TypeMirror managerType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(CacheManager.class.getCanonicalName()),
            keyElement.asType(),
            valueElement.asType());

        final TypeMirror cacheType = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(Cache.class.getCanonicalName()),
            keyElement.asType(),
            valueElement.asType());

        return new CacheMirrors(managerType, cacheType);
    }

    List<String> getCacheFields(CacheOperation operation, CacheMirrors mirror, AspectContext aspectContext) {
        final List<String> cacheFields = new ArrayList<>();
        for (CacheMeta.Manager manager : operation.meta().managers()) {
            final List<AnnotationSpec> managerTags;
            if (manager.tags().isEmpty()) {
                managerTags = List.of();
            } else if (manager.tags().size() == 1) {
                managerTags = List.of(AnnotationSpec.builder(Tag.class)
                    .addMember("value", manager.tags().get(0))
                    .build());
            } else {
                final String tagValue = manager.tags().stream()
                    .collect(Collectors.joining(", ", "{", "}"));

                managerTags = List.of(AnnotationSpec.builder(Tag.class)
                    .addMember("value", tagValue)
                    .build());
            }

            var fieldManager = aspectContext.fieldFactory().constructorParam(mirror.manager(), managerTags);
            var fieldCache = aspectContext.fieldFactory().constructorInitialized(mirror.cache(), CodeBlock.builder()
                .add("$L.getCache(\"$L\")", fieldManager, manager.name())
                .build());

            cacheFields.add(fieldCache);
        }

        return cacheFields;
    }

    String getKeyRecordParameters(CacheOperation operation, ExecutableElement method) {
        return String.join(", ", operation.meta().getParametersNames(method));
    }

    String getSuperMethod(ExecutableElement method, String superCall) {
        return method.getParameters().stream()
            .map(p -> CodeBlock.of("$L", p))
            .collect(joining(", ", superCall + "(", ")")).toString();
    }
}
