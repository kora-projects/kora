package io.koraframework.cache.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class CacheAnnotationProcessor extends AbstractKoraProcessor {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z][0-9a-zA-Z_]*");

    private static final ClassName ANNOTATION_CACHE = ClassName.get("io.koraframework.cache.annotation", "Cache");

    private static final ClassName CAFFEINE_CACHE = ClassName.get("io.koraframework.cache.caffeine", "CaffeineCache");
    private static final ClassName CAFFEINE_CACHE_FACTORY = ClassName.get("io.koraframework.cache.caffeine", "CaffeineCacheFactory");
    private static final ClassName CAFFEINE_CACHE_CONFIG = ClassName.get("io.koraframework.cache.caffeine", "CaffeineCacheConfig");
    private static final ClassName CAFFEINE_CACHE_IMPL = ClassName.get("io.koraframework.cache.caffeine", "AbstractCaffeineCache");

    private static final ClassName REDIS_TELEMETRY_FACTORY = ClassName.get("io.koraframework.cache.redis.telemetry", "RedisCacheTelemetryFactory");
    private static final ClassName REDIS_CACHE = ClassName.get("io.koraframework.cache.redis", "RedisCache");
    private static final ClassName REDIS_CACHE_IMPL = ClassName.get("io.koraframework.cache.redis", "AbstractRedisCache");
    private static final ClassName REDIS_CACHE_CONFIG = ClassName.get("io.koraframework.cache.redis", "RedisCacheConfig");
    private static final ClassName REDIS_CACHE_CLIENT = ClassName.get("io.koraframework.cache.redis", "RedisCacheClient");
    private static final ClassName REDIS_CACHE_MAPPER_KEY = ClassName.get("io.koraframework.cache.redis", "RedisCacheKeyMapper");
    private static final ClassName REDIS_CACHE_MAPPER_VALUE = ClassName.get("io.koraframework.cache.redis", "RedisCacheValueMapper");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_CACHE);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotated : annotatedElements.getOrDefault(ANNOTATION_CACHE, List.of())) {
            var element = annotated.element();
            if (!element.getKind().isInterface()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Cache annotation is intended to be used on interfaces, but was: " + element.getKind().name(), element);
                continue;
            }
            var cacheImpl = (TypeElement) element;
            var cacheContractType = getCacheSuperType(cacheImpl);
            if (cacheContractType == null) {
                continue;
            }

            var packageName = getPackage(cacheImpl);
            var cacheContractClassName = ClassName.get(cacheImpl);

            var configPath = getCacheTypeConfigPath(cacheImpl);
            if (!NAME_PATTERN.matcher(configPath).find()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Cache config path doesn't match pattern: " + NAME_PATTERN, cacheImpl);
                continue;
            }

            var cacheImplBase = getCacheImplBase(cacheImpl, cacheContractType);
            var implSpec = CommonUtils.extendsKeepAop(cacheImpl, getCacheImpl(cacheImpl).simpleName())
                .addAnnotation(AnnotationUtils.generated(CacheAnnotationProcessor.class))
                .addModifiers(Modifier.FINAL)
                .addMethod(getCacheConstructor(cacheImpl, configPath, cacheContractType))
                .superclass(cacheImplBase)
                .build();

            try {
                var implFile = JavaFile.builder(cacheContractClassName.packageName(), implSpec).build();
                implFile.writeTo(processingEnv.getFiler());

                var name = NameUtils.generatedType(cacheImpl, "Module");
                var moduleSpecBuilder = TypeSpec.interfaceBuilder(ClassName.get(packageName, name))
                    .addOriginatingElement(cacheImpl)
                    .addAnnotation(AnnotationUtils.generated(CacheAnnotationProcessor.class))
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(CommonClassNames.module)
                    .addMethod(getCacheMethodImpl(cacheImpl, cacheContractType))
                    .addMethod(getCacheMethodConfig(cacheImpl, cacheContractType));

                if (cacheContractType.rawType().equals(REDIS_CACHE)) {
                    var superTypes = processingEnv.getTypeUtils().directSupertypes(cacheImpl.asType());
                    var superType = superTypes.get(superTypes.size() - 1);
                    var keyType = ((DeclaredType) superType).getTypeArguments().get(0);
                    if (keyType instanceof DeclaredType dt && dt.asElement().getKind() == ElementKind.RECORD) {
                        moduleSpecBuilder.addMethod(getCacheRedisKeyMapperForRecord(cacheImpl, dt));
                    }
                }

                var moduleSpec = moduleSpecBuilder.build();

                final JavaFile moduleFile = JavaFile.builder(cacheContractClassName.packageName(), moduleSpec).build();
                moduleFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static List<TypeElement> collectInterfaces(Types types, TypeElement typeElement) {
        var result = new LinkedHashSet<TypeElement>();
        collectInterfaces(types, result, typeElement);
        return new ArrayList<>(result);
    }

    private static void collectInterfaces(Types types, Set<TypeElement> collectedElements, TypeElement typeElement) {
        if (collectedElements.add(typeElement)) {
            if (typeElement.asType().getKind() == TypeKind.ERROR) {
                throw new ProcessingErrorException("Element is error: %s".formatted(typeElement.toString()), typeElement);
            }
            for (var directlyImplementedInterface : typeElement.getInterfaces()) {
                var interfaceElement = (TypeElement) types.asElement(directlyImplementedInterface);
                collectInterfaces(types, collectedElements, interfaceElement);
            }
        }
    }

    @Nullable
    public DeclaredType findTypedInterface(TypeElement startElement, ClassName targetFqn) {
        Queue<DeclaredType> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        if (startElement.asType().getKind() == TypeKind.DECLARED) {
            queue.add((DeclaredType) startElement.asType());
        }

        while (!queue.isEmpty()) {
            DeclaredType currentType = queue.poll();
            TypeElement currentElement = (TypeElement) currentType.asElement();

            String signature = currentType.toString();
            if (visited.contains(signature)) {
                continue;
            }
            visited.add(signature);

            if (currentElement.getQualifiedName().contentEquals(targetFqn.canonicalName())) {
                return currentType;
            }

            List<? extends TypeMirror> supertypes = types.directSupertypes(currentType);
            for (TypeMirror superType : supertypes) {
                if (superType.getKind() == TypeKind.DECLARED) {
                    queue.add((DeclaredType) superType);
                }
            }
        }

        return null;
    }

    @Nullable
    private ParameterizedTypeName getCacheSuperType(TypeElement candidate) {
        var redisCache = findTypedInterface(candidate, REDIS_CACHE);
        var caffeineCache = findTypedInterface(candidate, CAFFEINE_CACHE);

        if (redisCache != null && caffeineCache != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Cache annotated interface can't implement both: %s and %s interfaces".formatted(
                REDIS_CACHE.canonicalName(), CAFFEINE_CACHE.canonicalName()
            ), candidate);
            return null;
        }

        if (redisCache != null) {
            return (ParameterizedTypeName) TypeName.get(redisCache);
        } else if (caffeineCache != null) {
            return (ParameterizedTypeName) TypeName.get(caffeineCache);
        }

        messager.printMessage(Diagnostic.Kind.ERROR, "@Cache is expected to implement any super type: %s or %s".formatted(
            REDIS_CACHE.canonicalName(), CAFFEINE_CACHE.canonicalName()
        ), candidate);
        return null;
    }

    private TypeName getCacheImplBase(TypeElement cacheContract, ParameterizedTypeName cacheType) {
        if (cacheType.rawType().equals(CAFFEINE_CACHE)) {
            return ParameterizedTypeName.get(CAFFEINE_CACHE_IMPL, cacheType.typeArguments().get(0), cacheType.typeArguments().get(1));
        } else if (cacheType.rawType().equals(REDIS_CACHE)) {
            return ParameterizedTypeName.get(REDIS_CACHE_IMPL, cacheType.typeArguments().get(0), cacheType.typeArguments().get(1));
        } else {
            throw new UnsupportedOperationException("Unknown type: " + cacheContract.getQualifiedName());
        }
    }

    private static String getCacheTypeConfigPath(TypeElement cacheContract) {
        var cacheAnnotation = Objects.requireNonNull(AnnotationUtils.findAnnotation(cacheContract, ANNOTATION_CACHE));
        return Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(cacheAnnotation, "value"));
    }

    private MethodSpec getCacheMethodConfig(TypeElement cacheImpl, ParameterizedTypeName cacheType) {
        final String configPath = getCacheTypeConfigPath(cacheImpl);
        final ClassName cacheContractName = ClassName.get(cacheImpl);
        var prefix = NameUtils.getOuterClassesAsPrefix(cacheImpl).substring(1) + cacheImpl.getSimpleName();
        var methodName = CommonUtils.decapitalize(prefix) + "_Config";

        final TypeName returnType;
        if (cacheType.rawType().equals(CAFFEINE_CACHE)) {
            returnType = CAFFEINE_CACHE_CONFIG;
        } else if (cacheType.rawType().equals(REDIS_CACHE)) {
            returnType = REDIS_CACHE_CONFIG;
        } else {
            throw new IllegalArgumentException("Unknown cache type: " + cacheType.rawType());
        }
        var extractorType = ParameterizedTypeName.get(CommonClassNames.configValueExtractor, returnType);

        return MethodSpec.methodBuilder(methodName)
            .addAnnotation(TagUtils.makeAnnotationSpec(cacheContractName))
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(extractorType, "extractor")
            .addStatement("return extractor.extract(config.get($S))", configPath)
            .returns(returnType)
            .build();
    }

    private MethodSpec getCacheRedisKeyMapperForRecord(TypeElement cacheImpl, DeclaredType keyType) {
        var keyElement = keyType.asElement();
        var cachePrefix = cacheImpl.getSimpleName().toString();
        var prefix = NameUtils.getOuterClassesAsPrefix(keyElement).substring(1) + keyElement.getSimpleName();
        if (!prefix.startsWith(cachePrefix)) {
            prefix = cachePrefix + "_" + prefix;
        }
        var methodName = CommonUtils.decapitalize(prefix) + "_RedisKeyMapper";

        var methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.defaultComponent);

        var recordFields = keyElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.RECORD_COMPONENT)
            .toList();

        var keyBuilder = CodeBlock.builder();
        var compositeKeyBuilder = CodeBlock.builder();
        var copyBuilder = CodeBlock.builder();
        copyBuilder.addStatement("var offset = 0");
        for (int i = 0; i < recordFields.size(); i++) {
            var recordField = recordFields.get(i);
            var mapperName = "keyMapper" + (i + 1);
            methodBuilder.addParameter(ParameterizedTypeName.get(REDIS_CACHE_MAPPER_KEY, TypeName.get(recordField.asType())), mapperName);

            var keyName = "_key" + (i + 1);
            keyBuilder.addStatement("var $L = $L.apply($T.requireNonNull(key.$L(), $S))",
                keyName, mapperName, Objects.class, recordField.getSimpleName().toString(),
                "Cache key '%s' field '%s' must be non null".formatted(keyElement.toString(), recordField.getSimpleName().toString()));

            if (i == 0) {
                compositeKeyBuilder.add("var _compositeKey = new byte[");
                for (int j = 0; j < recordFields.size(); j++) {
                    var compKeyName = "_key" + (j + 1);
                    if (j != 0) {
                        compositeKeyBuilder.add(" + $T.DELIMITER.length + $L.length", REDIS_CACHE_MAPPER_KEY, compKeyName);
                    } else {
                        compositeKeyBuilder.add("$L.length", compKeyName);
                    }
                }
                copyBuilder.addStatement("$T.arraycopy($L, 0, _compositeKey, 0, $L.length)", System.class, keyName, keyName);
                copyBuilder.addStatement("offset += $L.length", keyName);
            } else {
                copyBuilder.addStatement("$T.arraycopy($T.DELIMITER, 0, _compositeKey, offset, $T.DELIMITER.length)", System.class, REDIS_CACHE_MAPPER_KEY, REDIS_CACHE_MAPPER_KEY);
                copyBuilder.addStatement("offset += $T.DELIMITER.length", REDIS_CACHE_MAPPER_KEY);
                copyBuilder.addStatement("$T.arraycopy($L, 0, _compositeKey, offset, $L.length)", System.class, keyName, keyName);
                if (i != recordFields.size() - 1) {
                    copyBuilder.addStatement("offset += $L.length", keyName);
                }
            }
        }

        compositeKeyBuilder.addStatement("]");
        copyBuilder.addStatement("return _compositeKey");

        return methodBuilder
            .addCode(CodeBlock.builder()
                .beginControlFlow("return key -> ")
                .add(keyBuilder.build())
                .add(compositeKeyBuilder.build())
                .add(copyBuilder.build())
                .endControlFlow()
                .add(";")
                .build()
            )
            .returns(ParameterizedTypeName.get(REDIS_CACHE_MAPPER_KEY, TypeName.get(keyType)))
            .build();
    }

    private static ClassName getCacheImpl(TypeElement cacheContract) {
        var name = NameUtils.generatedType(cacheContract, "Impl");
        final ClassName cacheImplName = ClassName.get(cacheContract);
        return ClassName.get(cacheImplName.packageName(), name);
    }

    private MethodSpec getCacheMethodImpl(TypeElement cacheImpl, ParameterizedTypeName cacheType) {
        var cacheImplName = getCacheImpl(cacheImpl);
        var prefix = NameUtils.getOuterClassesAsPrefix(cacheImpl).substring(1) + cacheImpl.getSimpleName();
        var methodName = CommonUtils.decapitalize(prefix) + "_Impl";
        if (cacheType.rawType().equals(CAFFEINE_CACHE)) {
            return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(CAFFEINE_CACHE_CONFIG, "config")
                    .addAnnotation(TagUtils.makeAnnotationSpec(ClassName.get(cacheImpl)))
                    .build())
                .addParameter(CAFFEINE_CACHE_FACTORY, "factory")
                .addStatement("return new $T(config, factory)", cacheImplName)
                .returns(TypeName.get(cacheImpl.asType()))
                .build();
        }
        if (cacheType.rawType().equals(REDIS_CACHE)) {
            var keyType = cacheType.typeArguments().get(0);
            var valueType = cacheType.typeArguments().get(1);
            var keyMapperType = ParameterizedTypeName.get(REDIS_CACHE_MAPPER_KEY, keyType);
            var valueMapperType = ParameterizedTypeName.get(REDIS_CACHE_MAPPER_VALUE, valueType);

            final DeclaredType cacheDeclaredType = cacheImpl.getInterfaces().stream()
                .filter(i -> ClassName.get(i).equals(cacheType))
                .map(i -> (DeclaredType) i)
                .findFirst()
                .orElseThrow();

            var valueParamBuilder = ParameterSpec.builder(valueMapperType, "valueMapper");
            var valueTags = TagUtils.parseTagValue(cacheDeclaredType.getTypeArguments().get(1));
            if (valueTags != null) {
                valueParamBuilder.addAnnotation(TagUtils.makeAnnotationSpec(valueTags));
            }

            var keyParamBuilder = ParameterSpec.builder(keyMapperType, "keyMapper");
            var keyTags = TagUtils.parseTagValue(cacheDeclaredType.getTypeArguments().get(0));
            if (keyTags != null) {
                keyParamBuilder.addAnnotation(TagUtils.makeAnnotationSpec(keyTags));
            }

            return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(REDIS_CACHE_CONFIG, "config")
                    .addAnnotation(TagUtils.makeAnnotationSpec(cacheImpl))
                    .build())
                .addParameter(REDIS_CACHE_CLIENT, "redisClient")
                .addParameter(REDIS_TELEMETRY_FACTORY, "telemetryFactory")
                .addParameter(keyParamBuilder.build())
                .addParameter(valueParamBuilder.build())
                .addStatement("return new $T(config, redisClient, telemetryFactory, keyMapper, valueMapper)", cacheImplName)
                .returns(TypeName.get(cacheImpl.asType()))
                .build();
        }
        throw new IllegalArgumentException("Unknown cache type: " + cacheType.rawType());
    }

    private MethodSpec getCacheConstructor(TypeElement cacheImpl, String configPath, ParameterizedTypeName cacheContract) {
        if (cacheContract.rawType().equals(CAFFEINE_CACHE)) {
            return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(CAFFEINE_CACHE_CONFIG, "config")
                .addParameter(CAFFEINE_CACHE_FACTORY, "factory")
                .addStatement("super($S, $S, config, factory)", configPath, ClassName.get(cacheImpl).canonicalName())
                .build();
        }

        if (cacheContract.rawType().equals(REDIS_CACHE)) {
            var keyType = cacheContract.typeArguments().get(0);
            var valueType = cacheContract.typeArguments().get(1);
            var keyMapperType = ParameterizedTypeName.get(REDIS_CACHE_MAPPER_KEY, keyType);
            var valueMapperType = ParameterizedTypeName.get(REDIS_CACHE_MAPPER_VALUE, valueType);
            return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(REDIS_CACHE_CONFIG, "config")
                .addParameter(REDIS_CACHE_CLIENT, "redisClient")
                .addParameter(REDIS_TELEMETRY_FACTORY, "telemetryFactory")
                .addParameter(keyMapperType, "keyMapper")
                .addParameter(valueMapperType, "valueMapper")
                .addStatement("super($S, $S, config, redisClient, telemetryFactory, keyMapper, valueMapper)", configPath, ClassName.get(cacheImpl).canonicalName())
                .build();
        }

        throw new IllegalArgumentException("Unknown cache type: " + cacheContract.rawType());
    }

    private String getPackage(Element element) {
        return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    }
}
