package io.koraframework.openfeature.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import java.util.*;

public final class OpenfeatureAnnotationProcessor extends AbstractKoraProcessor {
    private static final ClassName SOURCE = ClassName.get("io.koraframework.openfeature.annotation", "OpenfeatureSource");
    private static final ClassName TYPE = ClassName.get("io.koraframework.openfeature.annotation", "OpenfeatureType");
    private static final ClassName KEY = ClassName.get("io.koraframework.openfeature.annotation", "OpenfeatureKey");
    private static final ClassName CLIENT = ClassName.get("dev.openfeature.sdk", "Client");
    private static final ClassName CONTEXT = ClassName.get("dev.openfeature.sdk", "EvaluationContext");
    private static final ClassName IMMUTABLE_CONTEXT = ClassName.get("dev.openfeature.sdk", "ImmutableContext");
    private static final ClassName MAPPER = ClassName.get("io.koraframework.openfeature.mapper", "OpenfeatureFlagMapper");
    private static final ClassName REGISTRAR = ClassName.get("io.koraframework.openfeature", "OpenfeatureFlagRegistrar");
    private static final ClassName FLAG_TYPE = ClassName.get("dev.openfeature.sdk", "FlagValueType");

    private record Flag(ExecutableElement method, TypeName type, String key, String sdkType, @Nullable String sdkMethod,
                        CommonUtils.@Nullable MappingData mapper) {}
    private record Mapper(TypeName type, @Nullable String tag) {}

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(SOURCE);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment round,
                           Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotated : annotatedElements.getOrDefault(SOURCE, List.of())) {
            if (!(annotated.element() instanceof TypeElement source) || source.getKind() != ElementKind.INTERFACE) {
                throw new ProcessingErrorException("@OpenfeatureSource requires an interface", annotated.element());
            }
            if (!source.getTypeParameters().isEmpty() || source.getModifiers().contains(Modifier.SEALED)) {
                throw new ProcessingErrorException("@OpenfeatureSource requires a non-generic, non-sealed interface", source);
            }
            generate(source);
        }
    }

    private void generate(TypeElement source) {
        var annotation = AnnotationUtils.findAnnotation(source, SOURCE);
        String path = AnnotationUtils.parseAnnotationValue(elements, annotation, "value");
        var clientTagType = AnnotationUtils.<javax.lang.model.type.TypeMirror>parseAnnotationValue(elements, annotation, "clientTag");
        var clientTag = clientTagType.toString().equals(CommonClassNames.tag.canonicalName()) ? null : clientTagType.toString();
        var flags = flags(source, path);
        var packageName = elements.getPackageOf(source).getQualifiedName().toString();
        var configName = ClassName.get(packageName, NameUtils.generatedType(source, "Config"));
        var implName = ClassName.get(packageName, NameUtils.generatedType(source, "Impl"));
        var moduleName = ClassName.get(packageName, NameUtils.generatedType(source, "Module"));
        var sourceType = TypeName.get(source.asType());

        var config = TypeSpec.interfaceBuilder(configName).addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(getClass()))
            .addAnnotation(AnnotationSpec.builder(ConfigClassNames.configSourceAnnotation).addMember("value", "$S", path).build())
            .addOriginatingElement(source);
        var impl = TypeSpec.classBuilder(implName).addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(sourceType).addAnnotation(AnnotationUtils.generated(getClass())).addOriginatingElement(source);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
            .addParameter(clientParameter(clientTag)).addParameter(configName, "config").addParameter(CONTEXT, "context");
        impl.addField(CLIENT, "client", Modifier.PRIVATE, Modifier.FINAL)
            .addField(configName, "config", Modifier.PRIVATE, Modifier.FINAL)
            .addField(CONTEXT, "context", Modifier.PRIVATE, Modifier.FINAL);
        constructor.addStatement("this.client = client").addStatement("this.config = config")
            .addStatement("this.context = new $T(context.getTargetingKey(), context.asMap())", IMMUTABLE_CONTEXT);
        var mappers = new LinkedHashMap<Mapper, String>();

        for (var flag : flags) {
            var method = flag.method();
            var methodName = method.getSimpleName().toString();
            var configMethod = MethodSpec.methodBuilder(methodName).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(flag.type().box());
            if (method.isDefault()) {
                configMethod.addAnnotation(Nullable.class);
            }
            var configMapping = CommonUtils.parseMapping(method).getMapping(CommonClassNames.configValueMapper);
            if (configMapping != null) {
                if (configMapping.mapperClass() != null) {
                    configMethod.addAnnotation(AnnotationSpec.builder(CommonClassNames.mapping)
                        .addMember("value", "$T.class", configMapping.mapperClass()).build());
                }
                if (configMapping.mapperClass() != null && configMapping.mapperTag() != null) {
                    configMethod.addAnnotation(configMapping.toTagAnnotation());
                }
            }
            config.addMethod(configMethod.build());

            var memberType = (ExecutableType) types.asMemberOf((DeclaredType) source.asType(), method);
            var implementation = MethodSpec.overriding(method, (DeclaredType) source.asType(), types);
            implementation.addStatement("var defaultValue = config.$L()", methodName);
            if (method.isDefault()) {
                implementation.beginControlFlow("if (defaultValue == null)")
                    .addStatement("defaultValue = $T.super.$L()", sourceType, methodName).endControlFlow();
            }
            implementation.addStatement("$T.requireNonNull(defaultValue, $S)", Objects.class, "Null default for " + flag.key());
            if (flag.sdkMethod() != null && flag.mapper() == null) {
                implementation.addStatement("return client.$L($S, defaultValue, context)", flag.sdkMethod(), flag.key());
            } else {
                var mapperInterface = ParameterizedTypeName.get(MAPPER, TypeName.get(memberType.getReturnType()).box());
                var mapping = flag.mapper();
                var mapperType = mapping == null || mapping.mapperClass() == null ? mapperInterface
                    : mapping.isGeneric() ? mapping.parameterized(flag.type().box()) : TypeName.get(mapping.mapperClass());
                var tag = mapping == null ? null : mapping.mapperTag();
                var mapperKey = new Mapper(mapperType, tag);
                var field = mappers.computeIfAbsent(mapperKey, key -> {
                    var name = "mapper" + mappers.size();
                    impl.addField(mapperType, name, Modifier.PRIVATE, Modifier.FINAL);
                    var parameter = ParameterSpec.builder(mapperType, name);
                    if (tag != null) {
                        parameter.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                    }
                    constructor.addParameter(parameter.build()).addStatement("this.$L = $L", name, name);
                    return name;
                });
                implementation.addStatement("return $L.map(client, $S, defaultValue, context)", field, flag.key());
            }
            impl.addMethod(implementation.build());
        }

        var args = mappers.isEmpty() ? "" : ", " + String.join(", ", mappers.values());
        var contextable = elements.getTypeElement("io.koraframework.openfeature.context.Contextable");
        if (types.isAssignable(types.erasure(source.asType()), types.erasure(contextable.asType()))) {
            impl.addMethod(MethodSpec.methodBuilder("withContext").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(sourceType).addParameter(CONTEXT, "context")
                .addStatement("return new $T(client, config, context$L)", implName, args).build());
        }
        impl.addMethod(constructor.build());

        var prefix = NameUtils.getOuterClassesAsPrefix(source).substring(1) + source.getSimpleName();
        prefix = Character.toLowerCase(prefix.charAt(0)) + prefix.substring(1);
        var factory = MethodSpec.methodBuilder(prefix + "FlagSource").addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(sourceType).addParameter(clientParameter(clientTag)).addParameter(configName, "config");
        for (var mapper : mappers.entrySet()) {
            var parameter = ParameterSpec.builder(mapper.getKey().type(), mapper.getValue());
            if (mapper.getKey().tag() != null) {
                parameter.addAnnotation(TagUtils.makeAnnotationSpec(mapper.getKey().tag()));
            }
            factory.addParameter(parameter.build());
        }
        factory.addStatement("return new $T(client, config, new $T()$L)", implName, IMMUTABLE_CONTEXT, args);
        var registration = MethodSpec.methodBuilder(prefix + "FlagRegistration")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT).returns(REGISTRAR);
        if (clientTag != null) {
            registration.addAnnotation(TagUtils.makeAnnotationSpec(clientTag));
        }
        var entries = CodeBlock.builder().add("return () -> $T.ofEntries(", Map.class);
        for (int i = 0; i < flags.size(); i++) {
            if (i > 0) {
                entries.add(", ");
            }
            entries.add("$T.entry($S, $T.$L)", Map.class, flags.get(i).key(), FLAG_TYPE, flags.get(i).sdkType());
        }
        entries.addStatement(")");
        registration.addCode(entries.build());
        var module = TypeSpec.interfaceBuilder(moduleName).addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module).addAnnotation(AnnotationUtils.generated(getClass()))
            .addOriginatingElement(source).addMethod(factory.build()).addMethod(registration.build());
        CommonUtils.safeWriteTo(processingEnv, JavaFile.builder(packageName, config.build()).build());
        CommonUtils.safeWriteTo(processingEnv, JavaFile.builder(packageName, impl.build()).build());
        CommonUtils.safeWriteTo(processingEnv, JavaFile.builder(packageName, module.build()).build());
    }

    private static ParameterSpec clientParameter(@Nullable String tag) {
        var parameter = ParameterSpec.builder(CLIENT, "client");
        if (tag != null) {
            parameter.addAnnotation(TagUtils.makeAnnotationSpec(tag));
        }
        return parameter.build();
    }

    private List<Flag> flags(TypeElement source, String path) {
        var flags = new ArrayList<Flag>();
        var keys = new HashSet<String>();
        for (var element : elements.getAllMembers(source)) {
            if (!(element instanceof ExecutableElement method) || element.getKind() != ElementKind.METHOD
                || method.getModifiers().contains(Modifier.STATIC) || method.getModifiers().contains(Modifier.PRIVATE)
                || ((TypeElement) method.getEnclosingElement()).getQualifiedName().contentEquals("java.lang.Object")) {
                continue;
            }
            if (method.getSimpleName().contentEquals("withContext") && method.getParameters().size() == 1
                && types.erasure(method.getParameters().getFirst().asType()).toString().equals(CONTEXT.canonicalName())) {
                continue;
            }
            if (!method.getParameters().isEmpty() || method.getReturnType().getKind() == TypeKind.VOID) {
                if (method.isDefault()) {
                    continue;
                }
                throw new ProcessingErrorException("OpenFeature flag methods must have no arguments and return a value", method);
            }
            if (!method.getTypeParameters().isEmpty()) {
                throw new ProcessingErrorException("OpenFeature flag methods must not have type parameters", method);
            }
            if (CommonUtils.isNullable(method)) {
                throw new ProcessingErrorException("OpenFeature flag methods must return non-null values", method);
            }
            var member = (ExecutableType) types.asMemberOf((DeclaredType) source.asType(), method);
            var type = TypeName.get(member.getReturnType());
            var mapper = CommonUtils.parseMapping(method).getMapping(MAPPER);
            var sdkType = switch (type.box().toString()) {
                case "java.lang.Boolean" -> "BOOLEAN";
                case "java.lang.String" -> "STRING";
                case "java.lang.Integer" -> "INTEGER";
                case "java.lang.Long" -> "LONG";
                case "java.lang.Double", "java.lang.Float" -> "DOUBLE";
                case "java.math.BigDecimal", "java.math.BigInteger", "java.util.UUID", "java.util.regex.Pattern",
                     "io.koraframework.common.util.Size" -> "STRING";
                default -> type.toString().startsWith("java.time.") || types.asElement(member.getReturnType()) instanceof TypeElement te
                    && te.getKind() == ElementKind.ENUM ? "STRING" : "OBJECT";
            };
            var sdkMethod = switch (type.box().toString()) {
                case "java.lang.Boolean" -> "getBooleanValue";
                case "java.lang.String" -> "getStringValue";
                case "java.lang.Integer" -> "getIntegerValue";
                case "java.lang.Long" -> "getLongValue";
                case "java.lang.Double" -> "getDoubleValue";
                case "dev.openfeature.sdk.Value" -> "getObjectValue";
                default -> null;
            };
            var override = AnnotationUtils.findAnnotation(method, TYPE);
            if (override != null) {
                var enumValue = AnnotationUtils.<VariableElement>parseAnnotationValue(elements, override, "value");
                var overriddenType = enumValue.getSimpleName().toString();
                if (!overriddenType.equals(sdkType) && mapper == null) {
                    throw new ProcessingErrorException("@OpenfeatureType requires a custom mapper when changing the flag type", method);
                }
                sdkType = overriddenType;
            }
            var key = path.isEmpty() ? method.getSimpleName().toString() : path + "." + method.getSimpleName();
            var keyAnnotation = AnnotationUtils.findAnnotation(method, KEY);
            if (keyAnnotation != null) {
                key = AnnotationUtils.parseAnnotationValue(elements, keyAnnotation, "value");
            }
            if (key.isBlank()) {
                throw new ProcessingErrorException("OpenFeature flag key must not be blank", method);
            }
            if (!keys.add(key)) {
                throw new ProcessingErrorException("Duplicate OpenFeature flag key: " + key, method);
            }
            flags.add(new Flag(method, type, key,
                sdkType, sdkMethod, mapper));
        }
        return flags;
    }
}
