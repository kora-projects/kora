package ru.tinkoff.kora.cache.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class CacheAnnotationProcessor extends AbstractKoraProcessor {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z][0-9a-zA-Z_]*");

    private static final ClassName ANNOTATION_CACHE = ClassName.get("ru.tinkoff.kora.cache.annotation", "Cache");

    private static final ClassName CAFFEINE_TELEMETRY = ClassName.get("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheTelemetry");
    private static final ClassName REDIS_TELEMETRY = ClassName.get("ru.tinkoff.kora.cache.redis", "RedisCacheTelemetry");

    private static final ClassName CAFFEINE_CACHE = ClassName.get("ru.tinkoff.kora.cache.caffeine", "CaffeineCache");
    private static final ClassName CAFFEINE_CACHE_FACTORY = ClassName.get("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheFactory");
    private static final ClassName CAFFEINE_CACHE_CONFIG = ClassName.get("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheConfig");
    private static final ClassName CAFFEINE_CACHE_IMPL = ClassName.get("ru.tinkoff.kora.cache.caffeine", "AbstractCaffeineCache");

    private static final ClassName REDIS_CACHE = ClassName.get("ru.tinkoff.kora.cache.redis", "RedisCache");
    private static final ClassName REDIS_CACHE_IMPL = ClassName.get("ru.tinkoff.kora.cache.redis", "AbstractRedisCache");
    private static final ClassName REDIS_CACHE_CONFIG = ClassName.get("ru.tinkoff.kora.cache.redis", "RedisCacheConfig");
    private static final ClassName REDIS_CACHE_CLIENT_SYNC = ClassName.get("ru.tinkoff.kora.cache.redis.client", "SyncRedisClient");
    private static final ClassName REDIS_CACHE_CLIENT_REACTIVE = ClassName.get("ru.tinkoff.kora.cache.redis.client", "ReactiveRedisClient");
    private static final ClassName REDIS_CACHE_MAPPER_KEY = ClassName.get("ru.tinkoff.kora.cache.redis", "RedisCacheKeyMapper");
    private static final ClassName REDIS_CACHE_MAPPER_VALUE = ClassName.get("ru.tinkoff.kora.cache.redis", "RedisCacheValueMapper");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_CACHE.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var cacheAnnotation = processingEnv.getElementUtils().getTypeElement(ANNOTATION_CACHE.canonicalName());
        if (cacheAnnotation == null) {
            return false;
        }
        for (var element : roundEnv.getElementsAnnotatedWith(cacheAnnotation)) {
            if (!element.getKind().isInterface()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Cache annotation is intended to be used on interfaces, but was: " + element.getKind().name(), element);
                continue;
            }
            var cacheContract = (TypeElement) element;
            var cacheContractType = getCacheSuperType(cacheContract);
            if (cacheContractType == null) {
                continue;
            }

            var packageName = getPackage(cacheContract);
            var cacheContractClassName = ClassName.get(cacheContract);

            var configPath = getCacheTypeConfigPath(cacheContract);
            if (!NAME_PATTERN.matcher(configPath).find()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Cache config path doesn't match pattern: " + NAME_PATTERN, cacheContract);
                continue;
            }

            var cacheImplBase = getCacheImplBase(cacheContract, cacheContractType);
            var implSpec = TypeSpec.classBuilder(getCacheImpl(cacheContract))
                .addModifiers(Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                    .addMember("value", CodeBlock.of("$S", CacheAnnotationProcessor.class.getCanonicalName())).build())
                .addMethod(getCacheConstructor(configPath, cacheContractType))
                .superclass(cacheImplBase)
                .addSuperinterface(cacheContract.asType())
                .build();

            try {
                var implFile = JavaFile.builder(cacheContractClassName.packageName(), implSpec).build();
                implFile.writeTo(processingEnv.getFiler());

                var moduleSpec = TypeSpec.interfaceBuilder(ClassName.get(packageName, "$%sModule".formatted(cacheContractClassName.simpleName())))
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                        .addMember("value", CodeBlock.of("$S", CacheAnnotationProcessor.class.getCanonicalName())).build())
                    .addAnnotation(CommonClassNames.module)
                    .addMethod(getCacheMethodImpl(cacheContract, cacheContractType))
                    .addMethod(getCacheMethodConfig(cacheContract, cacheContractType))
                    .build();

                final JavaFile moduleFile = JavaFile.builder(cacheContractClassName.packageName(), moduleSpec).build();
                moduleFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return false;
    }

    @Nullable
    private ParameterizedTypeName getCacheSuperType(TypeElement candidate) {
        var interfaces = candidate.getInterfaces();
        if (interfaces.size() != 1) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Cache annotated interface should implement one one interface and it should be one of: %s, %s".formatted(
                REDIS_CACHE.canonicalName(), CAFFEINE_CACHE.canonicalName()
            ));
            return null;
        }
        var superinterface = (DeclaredType) interfaces.get(0);
        var superinterfaceElement = (TypeElement) superinterface.asElement();
        if (superinterfaceElement.getQualifiedName().contentEquals(CAFFEINE_CACHE.canonicalName())) {
            return (ParameterizedTypeName) TypeName.get(superinterface);
        }
        if (superinterfaceElement.getQualifiedName().contentEquals(REDIS_CACHE.canonicalName())) {
            return (ParameterizedTypeName) TypeName.get(superinterface);
        }
        messager.printMessage(Diagnostic.Kind.ERROR, "@Cache is expected to be known super type %s or %s, but was %s".formatted(
            REDIS_CACHE.canonicalName(), CAFFEINE_CACHE.canonicalName(), superinterface
        ));
        return null;
    }

    private TypeName getCacheImplBase(TypeElement cacheContract, ParameterizedTypeName cacheType) {
        if (cacheType.rawType.equals(CAFFEINE_CACHE)) {
            return ParameterizedTypeName.get(CAFFEINE_CACHE_IMPL, cacheType.typeArguments.get(0), cacheType.typeArguments.get(1));
        } else if (cacheType.rawType.equals(REDIS_CACHE)) {
            return ParameterizedTypeName.get(REDIS_CACHE_IMPL, cacheType.typeArguments.get(0), cacheType.typeArguments.get(1));
        } else {
            throw new UnsupportedOperationException("Unknown implementation: " + cacheContract.getQualifiedName());
        }
    }

    private static String getCacheTypeConfigPath(TypeElement cacheContract) {
        var cacheAnnotation = Objects.requireNonNull(AnnotationUtils.findAnnotation(cacheContract, ANNOTATION_CACHE));
        return Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(cacheAnnotation, "value"));
    }

    private MethodSpec getCacheMethodConfig(TypeElement cacheContract, ParameterizedTypeName cacheType) {
        final String configPath = getCacheTypeConfigPath(cacheContract);
        final ClassName cacheContractName = ClassName.get(cacheContract);
        final String methodName = "%sConfig".formatted(cacheContractName.simpleName());
        final TypeName returnType;
        if (cacheType.rawType.equals(CAFFEINE_CACHE)) {
            returnType = CAFFEINE_CACHE_CONFIG;
        } else if (cacheType.rawType.equals(REDIS_CACHE)) {
            returnType = REDIS_CACHE_CONFIG;
        } else {
            throw new IllegalArgumentException("Unknown cache type: " + cacheType.rawType);
        }
        var extractorType = ParameterizedTypeName.get(CommonClassNames.configValueExtractor, returnType);

        return MethodSpec.methodBuilder(methodName)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag)
                .addMember("value", cacheContractName.simpleName() + ".class")
                .build())
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(extractorType, "extractor")
            .addStatement("return extractor.extract(config.get($S))", configPath)
            .returns(returnType)
            .build();
    }

    private static ClassName getCacheImpl(TypeElement cacheContract) {
        final ClassName cacheImplName = ClassName.get(cacheContract);
        return ClassName.get(cacheImplName.packageName(), "$%sImpl".formatted(cacheImplName.simpleName()));
    }

    private MethodSpec getCacheMethodImpl(TypeElement cacheContract, ParameterizedTypeName cacheType) {
        var cacheImplName = getCacheImpl(cacheContract);
        var methodName = "%sImpl".formatted(cacheImplName.simpleName());
        if (cacheType.rawType.equals(CAFFEINE_CACHE)) {
            return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(CAFFEINE_CACHE_CONFIG, "config")
                    .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag)
                        .addMember("value", cacheContract.getSimpleName().toString() + ".class")
                        .build())
                    .build())
                .addParameter(CAFFEINE_CACHE_FACTORY, "factory")
                .addParameter(CAFFEINE_TELEMETRY, "telemetry")
                .addStatement("return new $T(config, factory, telemetry)", cacheImplName)
                .returns(TypeName.get(cacheContract.asType()))
                .build();
        }
        if (cacheType.rawType.equals(REDIS_CACHE)) {
            var keyType = cacheType.typeArguments.get(0);
            var valueType = cacheType.typeArguments.get(1);
            var keyMapperType = ParameterizedTypeName.get(REDIS_CACHE_MAPPER_KEY, keyType);
            var valueMapperType = ParameterizedTypeName.get(REDIS_CACHE_MAPPER_VALUE, valueType);
            return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(REDIS_CACHE_CONFIG, "config")
                    .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag)
                        .addMember("value", cacheContract.getSimpleName().toString() + ".class")
                        .build())
                    .build())
                .addParameter(REDIS_CACHE_CLIENT_SYNC, "syncClient")
                .addParameter(REDIS_CACHE_CLIENT_REACTIVE, "reactiveClient")
                .addParameter(REDIS_TELEMETRY, "telemetry")
                .addParameter(keyMapperType, "keyMapper")
                .addParameter(valueMapperType, "valueMapper")
                .addStatement("return new $T(config, syncClient, reactiveClient, telemetry, keyMapper, valueMapper)", cacheImplName)
                .returns(TypeName.get(cacheContract.asType()))
                .build();
        }
        throw new IllegalArgumentException("Unknown cache type: " + cacheType.rawType);
    }

    private MethodSpec getCacheConstructor(String configPath, ParameterizedTypeName cacheContract) {
        if (cacheContract.rawType.equals(CAFFEINE_CACHE)) {
            return MethodSpec.constructorBuilder()
                .addParameter(CAFFEINE_CACHE_CONFIG, "config")
                .addParameter(CAFFEINE_CACHE_FACTORY, "factory")
                .addParameter(CAFFEINE_TELEMETRY, "telemetry")
                .addStatement("super($S, config, factory, telemetry)", configPath)
                .build();
        }
        if (cacheContract.rawType.equals(REDIS_CACHE)) {
            var keyType = cacheContract.typeArguments.get(0);
            var valueType = cacheContract.typeArguments.get(1);
            var keyMapperType = ParameterizedTypeName.get(REDIS_CACHE_MAPPER_KEY, keyType);
            var valueMapperType = ParameterizedTypeName.get(REDIS_CACHE_MAPPER_VALUE, valueType);
            return MethodSpec.constructorBuilder()
                .addParameter(REDIS_CACHE_CONFIG, "config")
                .addParameter(REDIS_CACHE_CLIENT_SYNC, "syncClient")
                .addParameter(REDIS_CACHE_CLIENT_REACTIVE, "reactiveClient")
                .addParameter(REDIS_TELEMETRY, "telemetry")
                .addParameter(keyMapperType, "keyMapper")
                .addParameter(valueMapperType, "valueMapper")
                .addStatement("super($S, config, syncClient, reactiveClient, telemetry, keyMapper, valueMapper)", configPath)
                .build();
        }
        throw new IllegalArgumentException("Unknown cache type: " + cacheContract.rawType);
    }

    private String getPackage(Element element) {
        return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    }
}
