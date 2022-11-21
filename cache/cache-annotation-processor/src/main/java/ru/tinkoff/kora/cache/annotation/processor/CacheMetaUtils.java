package ru.tinkoff.kora.cache.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.*;
import ru.tinkoff.kora.cache.annotation.processor.CacheMeta.Manager;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class CacheMetaUtils {

    private static final Set<Class<? extends Annotation>> CACHE_ANNOTATIONS = Set.of(Cacheable.class, Cacheables.class,
        CachePut.class, CachePuts.class,
        CacheInvalidate.class, CacheInvalidates.class);

    private CacheMetaUtils() {}

    static List<List<String>> getTags(ExecutableElement element) {
        final List<String> repetableNames = Stream.of(Cacheables.class, CachePuts.class, CacheInvalidates.class)
            .map(Class::getCanonicalName)
            .toList();

        return element.getAnnotationMirrors().stream()
            .filter(a -> CACHE_ANNOTATIONS.stream().anyMatch(type -> a.getAnnotationType().toString().contentEquals(type.getCanonicalName())))
            .flatMap(a -> {
                    if (repetableNames.contains(a.getAnnotationType().toString())) {
                        return a.getElementValues().entrySet().stream()
                            .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                            .flatMap(e -> {
                                final Object value = e.getValue().getValue();
                                if (value instanceof List<?>) {
                                    return ((List<?>) value).stream()
                                        .map(inner -> getInnerAnnotationTags(((AnnotationMirror) inner)));
                                } else {
                                    return Stream.of(List.of(value.toString()));
                                }
                            });
                    } else {
                        return Stream.of(getInnerAnnotationTags(a));
                    }
                }
            ).toList();
    }

    private static List<String> getInnerAnnotationTags(AnnotationMirror annotation) {
        return annotation.getElementValues().entrySet().stream()
            .filter(e -> e.getKey().getSimpleName().contentEquals("tags"))
            .flatMap(e -> {
                final Object value = e.getValue().getValue();
                return (value instanceof List<?>)
                    ? ((List<?>) value).stream().map(Object::toString)
                    : Stream.of(value.toString());
            })
            .toList();
    }

    static CacheMeta getCacheMeta(ExecutableElement method) {
        final List<Cacheable> cacheables = getCacheableAnnotations(method);
        final List<CachePut> puts = getCachePutAnnotations(method);
        final List<CacheInvalidate> invalidates = getCacheInvalidateAnnotations(method);

        final String className = method.getEnclosingElement().getSimpleName().toString();
        final String methodName = method.getSimpleName().toString();
        final CacheMeta.Origin origin = new CacheMeta.Origin(className, methodName);
        final List<List<String>> allTags = getTags(method);

        if (!cacheables.isEmpty()) {
            final List<Manager> managers = new ArrayList<>();
            final List<List<String>> managerParameters = new ArrayList<>();
            for (int i = 0; i < cacheables.size(); i++) {
                final Cacheable annotation = cacheables.get(i);
                final List<String> tags = allTags.get(i);
                final Manager manager = new Manager(annotation.name(), tags);
                managers.add(manager);

                final List<String> annotationParameters = List.of(annotation.parameters());
                for (List<String> managerParameter : managerParameters) {
                    if (!managerParameter.equals(annotationParameters)) {
                        throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                            annotation.getClass() + " parameters mismatch for different annotations for " + origin, method));
                    }
                }

                managerParameters.add(annotationParameters);
            }

            return new CacheMeta(CacheMeta.Type.GET, managers, managerParameters.get(0), origin);
        } else if (!puts.isEmpty()) {
            final List<Manager> managers = new ArrayList<>();
            final List<List<String>> managerParameters = new ArrayList<>();
            for (int i = 0; i < puts.size(); i++) {
                final CachePut annotation = puts.get(i);
                final List<String> tags = allTags.get(i);
                final Manager manager = new Manager(annotation.name(), tags);
                managers.add(manager);

                final List<String> annotationParameters = List.of(annotation.parameters());
                for (List<String> managerParameter : managerParameters) {
                    if (!managerParameter.equals(annotationParameters)) {
                        throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                            annotation.getClass() + " parameters mismatch for different annotations for " + origin, method));
                    }
                }

                managerParameters.add(annotationParameters);
            }

            return new CacheMeta(CacheMeta.Type.PUT, managers, managerParameters.get(0), origin);
        } else if (!invalidates.isEmpty()) {
            final List<Manager> managers = new ArrayList<>();
            final List<List<String>> managerParameters = new ArrayList<>();
            final boolean anyInvalidateAll = invalidates.stream().anyMatch(CacheInvalidate::invalidateAll);
            final boolean allInvalidateAll = invalidates.stream().allMatch(CacheInvalidate::invalidateAll);

            if (anyInvalidateAll && !allInvalidateAll) {
                throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                    CacheInvalidate.class + " not all annotations are marked 'invalidateAll' out of all for " + origin, method));
            }

            for (int i = 0; i < invalidates.size(); i++) {
                final CacheInvalidate annotation = invalidates.get(i);
                final List<String> tags = allTags.get(i);
                final Manager manager = new Manager(annotation.name(), tags);
                managers.add(manager);

                final List<String> annotationParameters = List.of(annotation.parameters());
                for (List<String> managerParameter : managerParameters) {
                    if (!managerParameter.equals(annotationParameters)) {
                        throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                            annotation.getClass() + " parameters mismatch for different annotations for " + origin, method));
                    }
                }

                managerParameters.add(annotationParameters);
            }

            final CacheMeta.Type type = (allInvalidateAll) ? CacheMeta.Type.EVICT_ALL : CacheMeta.Type.EVICT;
            return new CacheMeta(type, managers, managerParameters.get(0), origin);
        }

        throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
            "None of " + CACHE_ANNOTATIONS + " cache annotations found", method));
    }

    private static List<Cacheable> getCacheableAnnotations(ExecutableElement method) {
        final Cacheables annotations = method.getAnnotation(Cacheables.class);
        if (annotations != null) {
            return Arrays.stream(annotations.value()).toList();
        }

        final Cacheable annotation = method.getAnnotation(Cacheable.class);
        if (annotation != null) {
            return List.of(annotation);
        }

        return List.of();
    }

    private static List<CachePut> getCachePutAnnotations(ExecutableElement method) {
        final CachePuts annotations = method.getAnnotation(CachePuts.class);
        if (annotations != null) {
            return Arrays.stream(annotations.value()).toList();
        }

        final CachePut annotation = method.getAnnotation(CachePut.class);
        if (annotation != null) {
            return List.of(annotation);
        }

        return List.of();
    }

    private static List<CacheInvalidate> getCacheInvalidateAnnotations(ExecutableElement method) {
        final CacheInvalidates annotations = method.getAnnotation(CacheInvalidates.class);
        if (annotations != null) {
            return Arrays.stream(annotations.value()).toList();
        }

        final CacheInvalidate annotation = method.getAnnotation(CacheInvalidate.class);
        if (annotation != null) {
            return List.of(annotation);
        }

        return List.of();
    }
}
