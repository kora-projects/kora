package ru.tinkoff.kora.cache.annotation.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Stream;

import static ru.tinkoff.kora.cache.annotation.processor.CacheOperation.CacheExecution.Contract.ASYNC;
import static ru.tinkoff.kora.cache.annotation.processor.CacheOperation.CacheExecution.Contract.SYNC;

public final class CacheOperationUtils {

    private static final ClassName KEY_MAPPER_1 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper");
    private static final ClassName KEY_MAPPER_2 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper2");
    private static final ClassName KEY_MAPPER_3 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper3");
    private static final ClassName KEY_MAPPER_4 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper4");
    private static final ClassName KEY_MAPPER_5 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper5");
    private static final ClassName KEY_MAPPER_6 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper6");
    private static final ClassName KEY_MAPPER_7 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper7");
    private static final ClassName KEY_MAPPER_8 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper8");
    private static final ClassName KEY_MAPPER_9 = ClassName.get("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper9");

    private static final ClassName CACHE_ASYNC = ClassName.get("ru.tinkoff.kora.cache", "AsyncCache");
    private static final ClassName ANNOTATION_CACHEABLE = ClassName.get("ru.tinkoff.kora.cache.annotation", "Cacheable");
    private static final ClassName ANNOTATION_CACHEABLES = ClassName.get("ru.tinkoff.kora.cache.annotation", "Cacheables");
    private static final ClassName ANNOTATION_CACHE_PUT = ClassName.get("ru.tinkoff.kora.cache.annotation", "CachePut");
    private static final ClassName ANNOTATION_CACHE_PUTS = ClassName.get("ru.tinkoff.kora.cache.annotation", "CachePuts");
    private static final ClassName ANNOTATION_CACHE_INVALIDATE = ClassName.get("ru.tinkoff.kora.cache.annotation", "CacheInvalidate");
    private static final ClassName ANNOTATION_CACHE_INVALIDATES = ClassName.get("ru.tinkoff.kora.cache.annotation", "CacheInvalidates");

    private static final Set<String> CACHE_ANNOTATIONS = Set.of(
        ANNOTATION_CACHEABLE.canonicalName(), ANNOTATION_CACHEABLES.canonicalName(),
        ANNOTATION_CACHE_PUT.canonicalName(), ANNOTATION_CACHE_PUTS.canonicalName(),
        ANNOTATION_CACHE_INVALIDATE.canonicalName(), ANNOTATION_CACHE_INVALIDATES.canonicalName()
    );

    private CacheOperationUtils() {}

    public static CacheOperation getCacheOperation(ExecutableElement method, ProcessingEnvironment env, KoraAspect.AspectContext aspectContext) {
        final List<AnnotationMirror> cacheables = getRepeatedAnnotations(method, ANNOTATION_CACHEABLE.canonicalName(), ANNOTATION_CACHEABLES.canonicalName());
        final List<AnnotationMirror> puts = getRepeatedAnnotations(method, ANNOTATION_CACHE_PUT.canonicalName(), ANNOTATION_CACHE_PUTS.canonicalName());
        final List<AnnotationMirror> invalidates = getRepeatedAnnotations(method, ANNOTATION_CACHE_INVALIDATE.canonicalName(), ANNOTATION_CACHE_INVALIDATES.canonicalName());

        final String className = method.getEnclosingElement().getSimpleName().toString();
        final String methodName = method.getSimpleName().toString();
        final CacheOperation.Origin origin = new CacheOperation.Origin(className, methodName);

        if (!cacheables.isEmpty()) {
            if (!puts.isEmpty() || !invalidates.isEmpty()) {
                throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                    "Method must have Cache annotations with same operation type, but got multiple different operation types for " + origin, method));
            }

            return getOperation(method, cacheables, CacheOperation.Type.GET, env, aspectContext);
        } else if (!puts.isEmpty()) {
            if (!invalidates.isEmpty()) {
                throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                    "Method must have Cache annotations with same operation type, but got multiple different operation types for " + origin, method));
            }

            return getOperation(method, puts, CacheOperation.Type.PUT, env, aspectContext);
        } else if (!invalidates.isEmpty()) {
            var invalidateAlls = invalidates.stream()
                .flatMap(a -> a.getElementValues().entrySet().stream())
                .filter(e -> e.getKey().getSimpleName().contentEquals("invalidateAll"))
                .map(e -> ((boolean) e.getValue().getValue()))
                .toList();

            final boolean anyInvalidateAll = !invalidateAlls.isEmpty() && invalidateAlls.stream().anyMatch(v -> v);
            final boolean allInvalidateAll = !invalidateAlls.isEmpty() && invalidateAlls.stream().allMatch(v -> v);

            if (anyInvalidateAll && !allInvalidateAll) {
                throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                    ANNOTATION_CACHE_INVALIDATE.canonicalName() + " not all annotations are marked 'invalidateAll' out of all for " + origin, method));
            }

            final CacheOperation.Type type = (allInvalidateAll) ? CacheOperation.Type.EVICT_ALL : CacheOperation.Type.EVICT;
            return getOperation(method, invalidates, type, env, aspectContext);
        }

        throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
            "None of " + CACHE_ANNOTATIONS + " cache annotations found", method));
    }

    private static CacheOperation getOperation(ExecutableElement method,
                                               List<AnnotationMirror> cacheAnnotations,
                                               CacheOperation.Type type,
                                               ProcessingEnvironment env,
                                               KoraAspect.AspectContext aspectContext) {
        final String className = method.getEnclosingElement().getSimpleName().toString();
        final String methodName = method.getSimpleName().toString();
        final CacheOperation.Origin origin = new CacheOperation.Origin(className, methodName);

        final List<List<String>> cacheKeyArguments = new ArrayList<>();
        final List<CacheOperation.CacheExecution> cacheExecutions = new ArrayList<>();
        for (var annotation : cacheAnnotations) {
            var parameters = annotation.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("parameters"))
                .map(e -> ((List<?>) (e.getValue()).getValue()).stream()
                    .filter(a -> a instanceof AnnotationValue)
                    .map(a -> ((AnnotationValue) a).getValue().toString())
                    .toList())
                .findFirst()
                .orElse(Collections.emptyList());

            if (parameters.isEmpty()) {
                parameters = method.getParameters().stream()
                    .map(p -> p.getSimpleName().toString())
                    .toList();
            } else {
                for (String parameter : parameters) {
                    if (method.getParameters().stream().noneMatch(p -> p.getSimpleName().contentEquals(parameter))) {
                        throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                            "Unknown method parameter is declared: " + parameter, method));
                    }
                }
            }

            for (List<String> arguments : cacheKeyArguments) {
                if (!arguments.equals(parameters)) {
                    throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                        annotation.getClass() + " parameters mismatch for different annotations for: " + origin, method));
                }
            }

            cacheKeyArguments.add(parameters);

            final String cacheImpl = annotation.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue()))
                .findFirst()
                .orElseThrow();

            var cacheElement = env.getElementUtils().getTypeElement(cacheImpl);
            var fieldCache = aspectContext.fieldFactory().constructorParam(cacheElement.asType(), List.of());

            var superTypes = env.getTypeUtils().directSupertypes(cacheElement.asType());
            var superType = ((DeclaredType) superTypes.get(superTypes.size() - 1));


            final CacheOperation.CacheKey cacheKey;
            var cacheKeyMirror = MethodUtils.getGenericType(superType)
                .map(t -> ((DeclaredType) t))
                .orElseThrow();

            var mapper = getSuitableMapper(CommonUtils.parseMapping(method));
            if (mapper != null) {
                final List<AnnotationSpec> tags = mapper.mapperTags().isEmpty()
                    ? List.of()
                    : List.of(TagUtils.makeAnnotationSpec(mapper.mapperTags()));

                var fieldMapper = aspectContext.fieldFactory().constructorParam(mapper.mapperClass(), tags);
                cacheKey = new CacheOperation.CacheKey(cacheKeyMirror, CodeBlock.of("$L.map($L)", fieldMapper, String.join(", ", parameters)));
            } else if (parameters.size() == 1) {
                cacheKey = new CacheOperation.CacheKey(cacheKeyMirror, CodeBlock.of(parameters.get(0)));
            } else if (type == CacheOperation.Type.EVICT_ALL) {
                cacheKey = new CacheOperation.CacheKey(null, null);
            } else {
                final List<VariableElement> parameterResult = parameters.stream()
                    .flatMap(param -> method.getParameters().stream().filter(p -> p.getSimpleName().contentEquals(param)))
                    .map(p -> ((VariableElement) p))
                    .toList();

                var keyConstructor = findKeyConstructor(cacheKeyMirror, parameterResult, env.getTypeUtils());
                if (keyConstructor.isPresent()) {
                    cacheKey = new CacheOperation.CacheKey(cacheKeyMirror, CodeBlock.of("new $T($L)", cacheKeyMirror, String.join(", ", parameters)));
                } else {
                    if (parameters.size() > 9) {
                        throw new ProcessingErrorException("@%s doesn't support more than 9 method arguments for Cache Key"
                            .formatted(annotation.getAnnotationType().asElement().getSimpleName()), method);
                    }

                    if(parameters.isEmpty() && (type == CacheOperation.Type.GET || type == CacheOperation.Type.EVICT)) {
                        throw new ProcessingErrorException(
                            "@%s requires minimum 1 Cache Key method argument, but got 0".formatted(annotation.getAnnotationType().asElement().getSimpleName().toString()),
                            method);
                    }

                    var mapperType = getKeyMapper(cacheKeyMirror, parameterResult, env);
                    var fieldMapper = aspectContext.fieldFactory().constructorParam(mapperType, List.of());
                    cacheKey = new CacheOperation.CacheKey(cacheKeyMirror, CodeBlock.of("$L.map($L)", fieldMapper, String.join(", ", parameters)));
                }
            }

            var contractType = SYNC;
            if (env.getTypeUtils().directSupertypes(superType).stream().anyMatch(t -> t instanceof DeclaredType dt && dt.asElement().toString().equals(CACHE_ASYNC.canonicalName()))) {
                contractType = ASYNC;
            }

            cacheExecutions.add(new CacheOperation.CacheExecution(fieldCache, cacheElement, superType, contractType, cacheKey));
        }

        return new CacheOperation(type, cacheExecutions, origin);
    }

    @Nullable
    private static CommonUtils.MappingData getSuitableMapper(CommonUtils.MappersData mappers) {
        if (mappers.isEmpty() || mappers.mapperClasses() == null) {
            return null;
        }

        return Stream.of(
                mappers.getMapping(KEY_MAPPER_1),
                mappers.getMapping(KEY_MAPPER_2),
                mappers.getMapping(KEY_MAPPER_3),
                mappers.getMapping(KEY_MAPPER_4),
                mappers.getMapping(KEY_MAPPER_5),
                mappers.getMapping(KEY_MAPPER_6),
                mappers.getMapping(KEY_MAPPER_7),
                mappers.getMapping(KEY_MAPPER_8),
                mappers.getMapping(KEY_MAPPER_9))
            .filter(Objects::nonNull)
            .filter(m -> m.mapperClass() != null)
            .findFirst()
            .orElse(null);
    }

    private static DeclaredType getKeyMapper(DeclaredType cacheKeyMirror, List<VariableElement> parameters, ProcessingEnvironment env) {
        var mapper = switch (parameters.size()) {
            case 1 -> KEY_MAPPER_1;
            case 2 -> KEY_MAPPER_2;
            case 3 -> KEY_MAPPER_3;
            case 4 -> KEY_MAPPER_4;
            case 5 -> KEY_MAPPER_5;
            case 6 -> KEY_MAPPER_6;
            case 7 -> KEY_MAPPER_7;
            case 8 -> KEY_MAPPER_8;
            case 9 -> KEY_MAPPER_9;
            default -> throw new ProcessingErrorException("Cache doesn't support %s parameters for Cache Key".formatted(parameters.size()), parameters.get(0));
        };

        var args = new ArrayList<TypeMirror>();
        args.add(cacheKeyMirror);
        parameters.forEach(a -> args.add(a.asType()));

        var mapperElement = env.getElementUtils().getTypeElement(mapper.canonicalName());
        return env.getTypeUtils().getDeclaredType(mapperElement, args.toArray(TypeMirror[]::new));
    }

    private static Optional<ExecutableElement> findKeyConstructor(DeclaredType type, List<VariableElement> parameters, Types types) {
        final List<ExecutableElement> constructors = type.asElement().getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .map(e -> ((ExecutableElement) e))
            .filter(c -> c.getModifiers().contains(Modifier.PUBLIC))
            .filter(c -> c.getParameters().size() == parameters.size())
            .toList();

        if (constructors.isEmpty()) {
            return Optional.empty();
        }

        for (var constructor : constructors) {
            var constructorParams = constructor.getParameters();

            boolean isCandidate = true;
            for (int i = 0; i < parameters.size(); i++) {
                var methodParam = parameters.get(i);
                var constructorParam = constructorParams.get(i);
                if (!types.isSameType(methodParam.asType(), constructorParam.asType())) {
                    isCandidate = false;
                    break;
                }
            }

            if (isCandidate) {
                return Optional.of(constructor);
            }
        }

        for (var constructor : constructors) {
            var constructorParams = constructor.getParameters();

            boolean isCandidate = true;
            for (int i = 0; i < parameters.size(); i++) {
                var methodParam = parameters.get(i);
                var constructorParam = constructorParams.get(i);
                if (!types.isSubtype(methodParam.asType(), constructorParam.asType())) {
                    isCandidate = false;
                    break;
                }
            }

            if (isCandidate) {
                return Optional.of(constructor);
            }
        }

        return Optional.empty();
    }

    private static List<AnnotationMirror> getRepeatedAnnotations(Element element,
                                                                 String annotation,
                                                                 String parentAnnotation) {
        final List<AnnotationMirror> repeated = element.getAnnotationMirrors().stream()
            .filter(pa -> pa.getAnnotationType().toString().contentEquals(parentAnnotation))
            .flatMap(pa -> pa.getElementValues().entrySet().stream())
            .flatMap(e -> ((List<?>) e.getValue().getValue()).stream().map(AnnotationMirror.class::cast))
            .filter(a -> a.getAnnotationType().toString().contentEquals(annotation))
            .toList();

        if (!repeated.isEmpty()) {
            return repeated;
        }

        return element.getAnnotationMirrors().stream()
            .filter(a -> a.getAnnotationType().toString().contentEquals(annotation))
            .map(a -> ((AnnotationMirror) a))
            .toList();
    }
}
