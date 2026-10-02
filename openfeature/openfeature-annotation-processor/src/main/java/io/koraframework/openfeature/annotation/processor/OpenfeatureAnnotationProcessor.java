package io.koraframework.openfeature.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import io.koraframework.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class OpenfeatureAnnotationProcessor extends AbstractKoraProcessor {

    private enum FlagValueType {
        STRING("getStringValue"),
        INTEGER("getIntegerValue"),
        DOUBLE("getDoubleValue"),
        BOOLEAN("getBooleanValue"),
        UNKNOWN("");

        private final String method;

        FlagValueType(String method) {
            this.method = method;
        }
    }

    private static final ClassName classValue = ClassName.get("dev.openfeature.sdk", "Value");
    private static final ClassName classFlagType = ClassName.get("dev.openfeature.sdk", "FlagValueType");
    private static final ClassName classClient = ClassName.get("dev.openfeature.sdk", "Client");
    private static final ClassName classFlagRegistrar = ClassName.get("io.koraframework.openfeature", "OpenfeatureFlagRegistrar");
    private static final ClassName classContext = ClassName.get("dev.openfeature.sdk", "EvaluationContext");
    private static final ClassName classImmutableContext = ClassName.get("dev.openfeature.sdk", "ImmutableContext");

    private static final ClassName classMapper = ClassName.get("io.koraframework.openfeature", "OpenfeatureFlagMapper");
    private static final ClassName annotationSourceClass = ClassName.get("io.koraframework.openfeature.annotation", "OpenfeatureSource");
    private static final ClassName openfeatureTypeAnnotation = ClassName.get("io.koraframework.openfeature.annotation", "OpenfeatureType");

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(annotationSourceClass);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        final List<TypeElement> sources = getSources(processingEnv, roundEnv);
        for (TypeElement source : sources) {
            var configFields = generateFlagConfigFields(source);

            var configClassName = generateFlagConfig(source, configFields);
            var impl = generateFlagImpl(source, configClassName);

            var openfeatureModule = NameUtils.generatedType(source, "Module");
            var type = TypeSpec.interfaceBuilder(openfeatureModule)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationUtils.generated(OpenfeatureAnnotationProcessor.class))
                .addAnnotation(CommonClassNames.module)
                .addOriginatingElement(source);

            var registrarMethod = getFlagRegistrarMethod(source);
            type.addMethod(registrarMethod);
            var sourceMethod = getFlagImplMethod(source, impl, configClassName);
            type.addMethod(sourceMethod);

            var packageName = this.elements.getPackageOf(source).getQualifiedName().toString();
            JavaFile module = JavaFile.builder(packageName, type.build()).build();
            CommonUtils.safeWriteTo(this.processingEnv, module);
        }
    }

    private ClassName generateFlagConfig(TypeElement source, List<ConfigUtils.ConfigField> configFields) {
        AnnotationMirror annotationSource = AnnotationUtils.findAnnotation(source, annotationSourceClass);
        String sourcePath = AnnotationUtils.parseAnnotationValue(elements, annotationSource, "value");

        String configName = NameUtils.generatedType(source, "Config");
        var configType = TypeSpec.interfaceBuilder(configName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(OpenfeatureAnnotationProcessor.class))
            .addAnnotation(AnnotationSpec.builder(ConfigClassNames.configSourceAnnotation)
                .addMember("value", "$S", sourcePath)
                .build())
            .addOriginatingElement(source);

        for (var field : configFields) {
            var methodBuilder = MethodSpec.methodBuilder(field.name())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(field.typeName().box());

            if (field.isNullable() || field.hasDefault()) {
                methodBuilder.addAnnotation(Nullable.class);
            }

            configType.addMethod(methodBuilder.build());
        }

        String packageName = this.elements.getPackageOf(source).getQualifiedName().toString();
        JavaFile module = JavaFile.builder(packageName, configType.build()).build();
        CommonUtils.safeWriteTo(this.processingEnv, module);

        return ClassName.get(packageName, configName);
    }

    record FlagImpl(ClassName name, Map<Mapper, String> mappers) {

        record Mapper(TypeName type, Set<String> tags) {}
    }

    private FlagImpl generateFlagImpl(TypeElement source, ClassName configClassName) {
        TypeName sourceType = TypeName.get(source.asType());
        AnnotationMirror annotationSource = AnnotationUtils.findAnnotation(source, annotationSourceClass);
        String sourcePath = AnnotationUtils.parseAnnotationValue(elements, annotationSource, "value");

        String className = NameUtils.generatedType(source, "Impl");
        var builder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationUtils.generated(OpenfeatureAnnotationProcessor.class))
            .addOriginatingElement(source)
            .addSuperinterface(ClassName.get(source));

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
            .addParameter(classClient, "client")
            .addParameter(configClassName, "config")
            .addParameter(classContext, "context");

        builder.addField(classClient, "client", Modifier.PRIVATE, Modifier.FINAL);
        builder.addField(configClassName, "config", Modifier.PRIVATE, Modifier.FINAL);
        builder.addField(classContext, "context", Modifier.PRIVATE, Modifier.FINAL);

        var fieldCounter = new AtomicInteger(0);
        var mapperToField = new LinkedHashMap<FlagImpl.Mapper, String>();
        var flagMethods = getFlagMethods(source);
        for (var method : flagMethods) {
            var flagType = getFlagType(method);
            var methodName = method.getSimpleName().toString();
            var featureKey = sourcePath + "." + methodName;
            var methodBuilder = MethodSpec.overriding(method);

            var bodyBuilder = CodeBlock.builder();
            if (flagType == FlagValueType.UNKNOWN) {
                if (method.isDefault()) {
                    bodyBuilder.addStatement("var defaultValue = $T.requireNonNullElse(config.$L(), $L.super.$L())",
                        Objects.class, methodName, sourceType, methodName);
                } else {
                    bodyBuilder.addStatement("var defaultValue = config.$L()", methodName);
                }

                var mapping = CommonUtils.parseMapping(method).getMapping(classMapper);
                var mapperType = ParameterizedTypeName.get(classMapper, TypeName.get(method.getReturnType()));
                var mapperImplType = (mapping == null || mapping.mapperClass() == null)
                    ? mapperType
                    : TypeName.get(mapping.mapperClass());

                var mapperTags = (mapping != null && mapping.mapperTags() != null)
                    ? mapping.mapperTags()
                    : Set.<String>of();

                var mapperField = mapperToField.computeIfAbsent(new FlagImpl.Mapper(mapperImplType, mapperTags), k -> {
                    var fieldName = "mapper" + fieldCounter.incrementAndGet();
                    builder.addField(mapperType, fieldName, Modifier.PRIVATE, Modifier.FINAL);

                    var paramBuilder = ParameterSpec.builder(mapperImplType, fieldName);
                    if (!mapperTags.isEmpty()) {
                        paramBuilder.addAnnotation(TagUtils.makeAnnotationSpec(mapping.mapperTags()));
                    }

                    constructor.addParameter(paramBuilder.build());
                    return fieldName;
                });
                bodyBuilder.addStatement("return $N.map(client, $S, defaultValue, context)", mapperField, featureKey);
            } else {
                if (method.isDefault()) {
                    bodyBuilder.addStatement("var defaultValue = $T.requireNonNullElse(config.$L(), $L.super.$L())",
                        Objects.class, methodName, sourceType, methodName);
                } else {
                    bodyBuilder.addStatement("var defaultValue = config.$L()", methodName);
                }

                bodyBuilder.addStatement("return client.$L($S, defaultValue, context)",
                    flagType.method, featureKey);
            }

            builder.addMethod(methodBuilder.addCode(bodyBuilder.build()).build());
        }

        constructor
            .addStatement("this.client = client")
            .addStatement("this.config = config")
            .addStatement("this.context = context");
        for (String field : mapperToField.values()) {
            constructor.addStatement("this.$L = $L", field, field);
        }
        builder.addMethod(constructor.build());

        String packageName = this.elements.getPackageOf(source).getQualifiedName().toString();
        var name = ClassName.get(packageName, className);

        var hasWithContext = source.getInterfaces()
            .stream()
            .filter(t -> t.getKind() == TypeKind.DECLARED)
            .map(t -> ((DeclaredType) t).asElement())
            .anyMatch(e ->
                e.getSimpleName().toString().equals("Contextable")
            );

        if (hasWithContext) {
            var withContextBody = CodeBlock.builder();
            if (mapperToField.isEmpty()) {
                withContextBody.addStatement("return new $T(client, config, context)", name);
            } else {
                withContextBody.addStatement("return new $T(client, config, context, $L)", name, String.join(", ", mapperToField.values()));
            }

            var withContext = MethodSpec.methodBuilder("withContext")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(classContext, "context")
                .addCode(withContextBody.build())
                .returns(sourceType);

            builder.addMethod(withContext.build());
        }

        JavaFile module = JavaFile.builder(packageName, builder.build()).build();
        CommonUtils.safeWriteTo(this.processingEnv, module);

        return new FlagImpl(name, mapperToField);
    }

    private List<ConfigUtils.ConfigField> generateFlagConfigFields(TypeElement source) {
        var configFields = ConfigUtils.parseFields(this.types, source);
        if (configFields.isRight()) {
            for (var processingError : Objects.requireNonNull(configFields.right())) {
                processingError.print(this.processingEnv);
            }
        }

        for (ConfigUtils.ConfigField field : Objects.requireNonNull(configFields.left())) {
            if (field.isNullable()) {
                throw new ProcessingErrorException("Openfeature method can't have nullable parameters", source);
            }
        }

        return configFields.left();
    }

    private MethodSpec getFlagImplMethod(TypeElement source, FlagImpl impl, ClassName configClassName) {
        var sourceTypeName = TypeName.get(source.asType());
        var flagBuilder = MethodSpec.methodBuilder("flagSource")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(classClient, "client")
            .addParameter(configClassName, "config")
            .returns(sourceTypeName);

        impl.mappers.forEach((m, f) -> {
            var builder = ParameterSpec.builder(m.type, f);
            if (!m.tags().isEmpty()) {
                builder.addAnnotation(TagUtils.makeAnnotationSpec(m.tags()));
            }
            flagBuilder.addParameter(builder.build());
        });

        if (impl.mappers().isEmpty()) {
            flagBuilder.addStatement("return new $T(client, config, new $T())", impl.name(), classImmutableContext);
        } else {
            flagBuilder.addStatement("return new $T(client, config, new $T(), $L)", impl.name(), classImmutableContext, String.join(", ", impl.mappers().values()));
        }

        return flagBuilder.build();
    }

    private MethodSpec getFlagRegistrarMethod(TypeElement source) {
        var flagBuilder = MethodSpec.methodBuilder("flagRegistration")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(classFlagRegistrar);

        CodeBlock.Builder builder = CodeBlock.builder();
        builder.add("return () -> $T.ofEntries(\n", Map.class);
        builder.indent().indent();

        AnnotationMirror annotationSource = AnnotationUtils.findAnnotation(source, annotationSourceClass);
        String sourcePath = AnnotationUtils.parseAnnotationValue(elements, annotationSource, "value");
        final List<ExecutableElement> flagMethods = getFlagMethods(source);
        for (int i = 0; i < flagMethods.size(); i++) {
            ExecutableElement method = flagMethods.get(i);
            FlagValueType flagType = getFlagType(method);
            final String openfeatureFlagType;
            if (flagType == FlagValueType.UNKNOWN) {
                var override = AnnotationUtils.<VariableElement>parseAnnotationValueWithoutDefault(AnnotationUtils.findAnnotation(method, openfeatureTypeAnnotation), "value");
                if (override != null) {
                    openfeatureFlagType = override.getSimpleName().toString();
                } else {
                    openfeatureFlagType = "OBJECT";
                }
            } else {
                openfeatureFlagType = flagType.name();
            }

            builder.add("$T.entry($S, $T.$L)", Map.class,
                sourcePath + "." + method.getSimpleName(), classFlagType, openfeatureFlagType);
            if (i + 1 < flagMethods.size()) {
                builder.add(",\n");
            } else {
                builder.add("\n");
            }
        }
        builder.unindent().unindent().addStatement(")");

        flagBuilder.addCode(builder.build());
        return flagBuilder.build();
    }

    private FlagValueType getFlagType(ExecutableElement method) {
        var returnType = method.getReturnType();
        var typeName = TypeName.get(returnType).box();
        return switch (typeName.toString()) {
            case "java.lang.Boolean" -> FlagValueType.BOOLEAN;
            case "java.lang.String" -> FlagValueType.STRING;
            case "java.lang.Integer" -> FlagValueType.INTEGER;
            case "java.lang.Double" -> FlagValueType.DOUBLE;
            default -> FlagValueType.UNKNOWN;
        };
    }

    private List<TypeElement> getSources(ProcessingEnvironment processEnv, RoundEnvironment roundEnv) {
        final TypeElement annotation = processEnv.getElementUtils().getTypeElement(annotationSourceClass.canonicalName());
        return roundEnv.getElementsAnnotatedWith(annotation).stream()
            .filter(e -> e instanceof TypeElement && e.getKind() == ElementKind.INTERFACE)
            .map(e -> ((TypeElement) e))
            .toList();
    }

    private List<ExecutableElement> getFlagMethods(TypeElement source) {
        return source.getEnclosedElements().stream()
            .filter(m -> m instanceof ExecutableElement && m.getKind() == ElementKind.METHOD)
            .map(m -> ((ExecutableElement) m))
            .toList();
    }
}
