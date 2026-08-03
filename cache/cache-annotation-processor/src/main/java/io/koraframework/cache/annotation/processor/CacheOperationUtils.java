package io.koraframework.cache.annotation.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.aop.annotation.processor.KoraAspect;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Stream;

public final class CacheOperationUtils {

    private static final ClassName KEY_MAPPER_1 = ClassName.get("io.koraframework.cache", "CacheKeyMapper");
    private static final ClassName KEY_MAPPER_2 = ClassName.get("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper2");
    private static final ClassName KEY_MAPPER_3 = ClassName.get("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper3");
    private static final ClassName KEY_MAPPER_4 = ClassName.get("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper4");
    private static final ClassName KEY_MAPPER_5 = ClassName.get("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper5");
    private static final ClassName KEY_MAPPER_6 = ClassName.get("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper6");
    private static final ClassName KEY_MAPPER_7 = ClassName.get("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper7");
    private static final ClassName KEY_MAPPER_8 = ClassName.get("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper8");
    private static final ClassName KEY_MAPPER_9 = ClassName.get("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper9");

    public static final ClassName ANNOTATION_CACHEABLE = ClassName.get("io.koraframework.cache.annotation", "Cacheable");
    public static final ClassName ANNOTATION_CACHEABLES = ClassName.get("io.koraframework.cache.annotation", "Cacheables");
    public static final ClassName ANNOTATION_CACHE_PUT = ClassName.get("io.koraframework.cache.annotation", "CachePut");
    public static final ClassName ANNOTATION_CACHE_PUTS = ClassName.get("io.koraframework.cache.annotation", "CachePuts");
    public static final ClassName ANNOTATION_CACHE_INVALIDATE = ClassName.get("io.koraframework.cache.annotation", "CacheInvalidate");
    public static final ClassName ANNOTATION_CACHE_INVALIDATES = ClassName.get("io.koraframework.cache.annotation", "CacheInvalidates");
    public static final ClassName ANNOTATION_CACHE_INVALIDATE_ALL = ClassName.get("io.koraframework.cache.annotation", "CacheInvalidateAll");
    public static final ClassName ANNOTATION_CACHE_INVALIDATE_ALLS = ClassName.get("io.koraframework.cache.annotation", "CacheInvalidateAlls");
    public static final ClassName CAFFEINE_CACHE = ClassName.get("io.koraframework.cache.caffeine", "CaffeineCache");

    private static final Set<String> CACHE_ANNOTATIONS = Set.of(
        ANNOTATION_CACHEABLE.canonicalName(), ANNOTATION_CACHEABLES.canonicalName(),
        ANNOTATION_CACHE_PUT.canonicalName(), ANNOTATION_CACHE_PUTS.canonicalName(),
        ANNOTATION_CACHE_INVALIDATE.canonicalName(), ANNOTATION_CACHE_INVALIDATES.canonicalName(),
        ANNOTATION_CACHE_INVALIDATE_ALL.canonicalName(), ANNOTATION_CACHE_INVALIDATE_ALLS.canonicalName()
    );

    private CacheOperationUtils() {}

    public static CacheOperation getCacheOperation(ExecutableElement method, ProcessingEnvironment env, KoraAspect.AspectContext aspectContext) {
        final List<AnnotationMirror> cacheables = getRepeatedAnnotations(method, ANNOTATION_CACHEABLE.canonicalName(), ANNOTATION_CACHEABLES.canonicalName());
        final List<AnnotationMirror> puts = getRepeatedAnnotations(method, ANNOTATION_CACHE_PUT.canonicalName(), ANNOTATION_CACHE_PUTS.canonicalName());
        final List<AnnotationMirror> invalidates = getRepeatedAnnotations(method, ANNOTATION_CACHE_INVALIDATE.canonicalName(), ANNOTATION_CACHE_INVALIDATES.canonicalName());
        final List<AnnotationMirror> invalidateAlls = getRepeatedAnnotations(method, ANNOTATION_CACHE_INVALIDATE_ALL.canonicalName(), ANNOTATION_CACHE_INVALIDATE_ALLS.canonicalName());

        final String className = method.getEnclosingElement().getSimpleName().toString();
        final String methodName = method.getSimpleName().toString();
        final CacheOperation.Origin origin = new CacheOperation.Origin(className, methodName);

        if (!cacheables.isEmpty()) {
            if (!puts.isEmpty() || !invalidates.isEmpty() || !invalidateAlls.isEmpty()) {
                throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                    mixedOperationTypesError(origin), method));
            }

            return getOperation(method, cacheables, CacheOperation.Type.GET, env, aspectContext);
        } else if (!puts.isEmpty()) {
            if (!invalidates.isEmpty() || !invalidateAlls.isEmpty()) {
                throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                    mixedOperationTypesError(origin), method));
            }

            return getOperation(method, puts, CacheOperation.Type.PUT, env, aspectContext);
        } else if (!invalidates.isEmpty()) {
            if (!invalidateAlls.isEmpty()) {
                throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                    """
                    Cache operation annotations on '%s' mix @CacheInvalidate and @CacheInvalidateAll.

                    Fix: use either key-based invalidation annotations or invalidate-all annotations on the same method, not both.
                    """.formatted(origin).trim(), method));
            }

            final CacheOperation.Type type = CacheOperation.Type.EVICT;
            return getOperation(method, invalidates, type, env, aspectContext);
        } else if (!invalidateAlls.isEmpty()) {
            final CacheOperation.Type type = CacheOperation.Type.EVICT_ALL;
            return getOperation(method, invalidateAlls, type, env, aspectContext);
        }

        throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
            """
            No cache operation annotation found on method '%s'.

            Expected one of: %s.
            Fix: annotate the method with exactly one cache operation kind.
            """.formatted(method.getSimpleName(), CACHE_ANNOTATIONS), method));
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
                .filter(e -> e.getKey().getSimpleName().contentEquals("args"))
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
                            """
                            Cache key references unknown method parameter '%s'.

                            Available parameters on '%s': %s.
                            Fix: update annotation args to use existing method parameter names.
                            """.formatted(parameter, method.getSimpleName(), method.getParameters().stream().map(p -> p.getSimpleName().toString()).toList()).trim(), method));
                    }
                }
            }

            for (List<String> arguments : cacheKeyArguments) {
                if (!arguments.equals(parameters)) {
                    throw new ProcessingErrorException(new ProcessingError(Diagnostic.Kind.ERROR,
                        """
                        Cache annotations on '%s' use different key argument lists.

                        Expected same args for every cache annotation in one operation, got %s and %s.
                        Fix: make all repeated cache annotations use identical args.
                        """.formatted(origin, arguments, parameters).trim(), method));
                }
            }

            cacheKeyArguments.add(parameters);

            final String cacheImpl = annotation.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .map(e -> String.valueOf(e.getValue().getValue()))
                .findFirst()
                .orElseThrow();
            final boolean async = annotation.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("mode"))
                .map(e -> {
                    var value = String.valueOf(e.getValue().getValue());
                    return value.equals("ASYNC") || value.endsWith(".ASYNC");
                })
                .findFirst()
                .orElse(false);

            var cacheElement = env.getElementUtils().getTypeElement(cacheImpl);
            var fieldCache = aspectContext.fieldFactory().constructorParam(cacheElement.asType(), List.of());

            var superTypes = env.getTypeUtils().directSupertypes(cacheElement.asType());
            var superType = ((DeclaredType) superTypes.get(superTypes.size() - 1));
            var caffeineCacheElement = env.getElementUtils().getTypeElement(CAFFEINE_CACHE.canonicalName());
            var isCaffeine = caffeineCacheElement != null && env.getTypeUtils().isSubtype(
                env.getTypeUtils().erasure(cacheElement.asType()),
                env.getTypeUtils().erasure(caffeineCacheElement.asType())
            );
            if (async && isCaffeine) {
                env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "Cache async mode is ignored for CaffeineCache " + cacheElement.getQualifiedName(),
                    method);
            }


            final CacheOperation.CacheKey cacheKey;
            var cacheKeyMirror = MethodUtils.getGenericType(superType)
                .map(t -> ((DeclaredType) t))
                .orElseThrow();

            var mapper = getSuitableMapper(CommonUtils.parseMapping(method));
            if (mapper != null) {
                final List<AnnotationSpec> tags = mapper.mapperTag() == null
                    ? List.of()
                    : List.of(TagUtils.makeAnnotationSpec(mapper.mapperTag()));

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
                        throw new ProcessingErrorException("""
                            @%s does not support more than 9 method arguments for Cache Key, but '%s' uses %d.

                            Fix: provide a custom CacheKeyMapper with @Mapping, or reduce the cache key arguments to at most 9.
                            """.formatted(annotation.getAnnotationType().asElement().getSimpleName(), method.getSimpleName(), parameters.size()).trim(), method);
                    }

                    if (parameters.isEmpty() && (type == CacheOperation.Type.GET || type == CacheOperation.Type.EVICT)) {
                        throw new ProcessingErrorException(
                            """
                            @%s on '%s' requires at least one Cache Key method argument, but got 0.

                            Fix: add a method parameter used as the key, specify args explicitly, or use @CacheInvalidateAll for invalidate-all behavior.
                            """.formatted(annotation.getAnnotationType().asElement().getSimpleName(), method.getSimpleName()).trim(),
                            method);
                    }

                    var mapperType = getKeyMapper(cacheKeyMirror, parameterResult, env);
                    var fieldMapper = aspectContext.fieldFactory().constructorParam(mapperType, List.of());
                    cacheKey = new CacheOperation.CacheKey(cacheKeyMirror, CodeBlock.of("$L.map($L)", fieldMapper, String.join(", ", parameters)));
                }
            }

            cacheExecutions.add(new CacheOperation.CacheExecution(fieldCache, cacheElement, superType, cacheKey, async, isCaffeine));
        }

        return new CacheOperation(type, cacheExecutions, origin);
    }

    private static CommonUtils.@Nullable MappingData getSuitableMapper(CommonUtils.MappersData mappers) {
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
            default -> throw new ProcessingErrorException("""
                Cache key has unsupported parameter count: %d.

                Supported built-in CacheKeyMapper arity is 1..9.
                Fix: provide a custom CacheKeyMapper with @Mapping, or reduce the cache key arguments.
                """.formatted(parameters.size()).trim(), parameters.get(0));
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

    private static String mixedOperationTypesError(CacheOperation.Origin origin) {
        return """
            Cache method '%s' mixes different cache operation annotation types.

            Fix: use only one operation kind per method: @Cacheable, @CachePut, @CacheInvalidate, or @CacheInvalidateAll.
            """.formatted(origin).trim();
    }
}
