package io.koraframework.config.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigParserGenerator {
    private final Types types;
    private final Elements elements;
    private final ProcessingEnvironment processingEnv;

    public ConfigParserGenerator(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.processingEnv = processingEnv;
    }

    public Either<Void, List<ProcessingError>> generateForInterface(DeclaredType targetType) {
        var element = (TypeElement) targetType.asElement();
        var f = ConfigUtils.parseFields(this.types, element);
        if (f.isRight()) {
            return Either.right(f.right());
        }
        var typeName = NameUtils.generatedType(element, ConfigClassNames.configValueMapper);
        var typeBuilder = TypeSpec.classBuilder(typeName)
            .addOriginatingElement(element)
            .addAnnotation(AnnotationUtils.generated(ConfigParserGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(ConfigClassNames.configValueMapper, TypeName.get(targetType)))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var fields = Objects.requireNonNull(f.left());
        var defaultsType = buildDefaultsType(targetType, element, fields);
        var packageName = elements.getPackageOf(element).getQualifiedName().toString();
        var implClassName = ClassName.get(packageName, typeName, element.getSimpleName().toString() + "_Impl");
        if (defaultsType != null) {
            typeBuilder.addType(defaultsType);
            var defaultImplClassName = ClassName.get(packageName, typeName, element.getSimpleName().toString() + "_Defaults");
            var field = FieldSpec.builder(defaultImplClassName, "DEFAULTS", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", defaultImplClassName);
            typeBuilder.addField(field.build());
        }
        var constructor = buildConstructor(typeBuilder, fields);
        typeBuilder.addMethod(constructor);
        typeBuilder.addMethod(buildExtractMethod(element, TypeName.get(targetType), implClassName, fields));

        for (var field : fields) {
            typeBuilder.addField(FieldSpec.builder(ConfigClassNames.pathElementKey, "_" + field.name() + "_path", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(CodeBlock.of("$T.get($S)", ConfigClassNames.pathElement, field.name()))
                .build());
            var parseFieldMethod = this.buildParseField(element, field);
            typeBuilder.addMethod(parseFieldMethod);
        }

        var configRecord = buildConfigInterfaceRecord(element, fields);
        typeBuilder.addType(configRecord);

        var parserType = typeBuilder.build();

        var javaFile = JavaFile.builder(packageName, parserType).build();
        CommonUtils.safeWriteTo(processingEnv, javaFile);
        return Either.left(null);
    }

    private MethodSpec buildExtractMethod(TypeElement element, TypeName typeName, ClassName implClassName, List<ConfigUtils.ConfigField> fields) {
        var constructors = CommonUtils.findConstructors(element, m -> m.contains(Modifier.PUBLIC));
        var emptyConstructor = constructors.stream().filter(e -> e.getParameters().isEmpty()).findFirst().orElse(null);
        var nonEmptyConstructor = constructors.stream().filter(e -> !e.getParameters().isEmpty()).findFirst().orElse(null);
        var constructorParams = nonEmptyConstructor == null ? Set.of() : nonEmptyConstructor.getParameters().stream().map(VariableElement::getSimpleName).map(Objects::toString).collect(Collectors.toSet());

        var rootParse = MethodSpec.methodBuilder("map")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(typeName.withoutAnnotations());
        rootParse.addParameter(ParameterizedTypeName.get(ConfigClassNames.configValue, WildcardTypeName.subtypeOf(ClassName.OBJECT)), "_sourceValue");
        rootParse.beginControlFlow("if (_sourceValue instanceof $T.NullValue _nullValue)", ConfigClassNames.configValue);

        var annotation = AnnotationUtils.findAnnotation(element, ConfigClassNames.configValueMapperAnnotation);
        if (annotation == null || Boolean.TRUE.equals(Objects.requireNonNullElse(AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "mapNullAsEmptyObject"), true))) {
            rootParse.addStatement("_sourceValue = new $T.ObjectValue(_sourceValue.origin(), $T.of())", ConfigClassNames.configValue, Map.class);
        } else {
            rootParse.addStatement("return null");
        }
        rootParse.endControlFlow();
        rootParse.addStatement("var _config = _sourceValue.asObject()");
        for (var field : fields) {
            rootParse.addStatement("var $N = this.parse_$L(_config)", field.name(), field.name());
        }
        if (element.getKind() == ElementKind.CLASS) {
            if (element.getTypeParameters().isEmpty()) {
                rootParse.addCode("var _result = new $T(", implClassName);
            } else {
                rootParse.addCode("var _result = new $T<>(", implClassName);
            }
            if (nonEmptyConstructor != null && emptyConstructor == null) {
                for (int i = 0; i < nonEmptyConstructor.getParameters().size(); i++) {
                    if (i > 0) {
                        rootParse.addCode(", ");
                    }
                    rootParse.addCode("$N", nonEmptyConstructor.getParameters().get(i).getSimpleName());
                }
            }
            rootParse.addCode(");\n");
            for (var field : fields) {
                if (!constructorParams.contains(field.name()) || emptyConstructor != null) {
                    rootParse.addStatement("_result.set$N($N)", CommonUtils.capitalize(field.name()), field.name());
                }
            }
            rootParse.addStatement("return _result");
        } else {
            var returnCodeBlock = CodeBlock.builder();
            if (element.getTypeParameters().isEmpty()) {
                returnCodeBlock.add("return new $T(\n", implClassName);
            } else {
                returnCodeBlock.add("return new $T<>(\n", implClassName);
            }
            for (int i = 0; i < fields.size(); i++) {
                var field = fields.get(i);
                if (i > 0) {
                    returnCodeBlock.add(",\n");
                }
                returnCodeBlock.add("  $N", field.name());
            }
            rootParse.addCode(returnCodeBlock.add("\n);\n").build());
        }
        return rootParse.build();
    }

    public Either<Void, List<ProcessingError>> generateForRecord(DeclaredType targetType) {
        var element = (TypeElement) targetType.asElement();
        var f = ConfigUtils.parseFields(this.types, element);
        if (f.isRight()) {
            return Either.right(f.right());
        }
        var typeName = NameUtils.generatedType(element, ConfigClassNames.configValueMapper);
        var typeBuilder = TypeSpec.classBuilder(typeName)
            .addOriginatingElement(element)
            .addAnnotation(AnnotationUtils.generated(ConfigParserGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(ConfigClassNames.configValueMapper, TypeName.get(targetType)))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var fields = Objects.requireNonNull(f.left());
        var implClassName = ClassName.get(element);
        var constructor = buildConstructor(typeBuilder, fields);
        typeBuilder.addMethod(constructor);
        typeBuilder.addMethod(buildExtractMethod(element, TypeName.get(targetType), implClassName, fields));

        for (var field : fields) {
            typeBuilder.addField(FieldSpec.builder(ConfigClassNames.pathElementKey, "_" + field.name() + "_path", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(CodeBlock.of("$T.get($S)", ConfigClassNames.pathElement, field.name()))
                .build());
            var parseFieldMethod = this.buildParseField(element, field);
            typeBuilder.addMethod(parseFieldMethod);
        }

        var packageName = elements.getPackageOf(element).getQualifiedName().toString();
        var javaFile = JavaFile.builder(packageName, typeBuilder.build()).build();
        CommonUtils.safeWriteTo(processingEnv, javaFile);
        return Either.left(null);
    }

    public Either<Void, List<ProcessingError>> generateForPojo(DeclaredType targetType) {
        var element = (TypeElement) targetType.asElement();
        var f = ConfigUtils.parseFields(this.types, element);
        if (f.isRight()) {
            return Either.right(f.right());
        }
        var typeName = NameUtils.generatedType(element, ConfigClassNames.configValueMapper);
        var typeBuilder = TypeSpec.classBuilder(typeName)
            .addOriginatingElement(element)
            .addAnnotation(AnnotationUtils.generated(ConfigParserGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(ConfigClassNames.configValueMapper, TypeName.get(targetType)))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var fields = Objects.requireNonNull(f.left());

        var implClassName = ClassName.get(element);
        var hasDefault = CommonUtils.findConstructors(element, m -> m.contains(Modifier.PUBLIC)).stream().anyMatch(e -> e.getParameters().isEmpty());
        if (hasDefault) {
            var defaults = FieldSpec.builder(implClassName, "DEFAULTS", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(CodeBlock.of("new $T()", implClassName));
            typeBuilder.addField(defaults.build());
        }


        var constructor = buildConstructor(typeBuilder, fields);
        typeBuilder.addMethod(constructor);
        typeBuilder.addMethod(buildExtractMethod(element, TypeName.get(targetType), implClassName, fields));

        for (var field : fields) {
            typeBuilder.addField(FieldSpec.builder(ConfigClassNames.pathElementKey, "_" + field.name() + "_path", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(CodeBlock.of("$T.get($S)", ConfigClassNames.pathElement, field.name()))
                .build());
            var parseFieldMethod = this.buildParseField(element, field);
            typeBuilder.addMethod(parseFieldMethod);
        }

        var packageName = elements.getPackageOf(element).getQualifiedName().toString();
        var javaFile = JavaFile.builder(packageName, typeBuilder.build()).build();
        CommonUtils.safeWriteTo(processingEnv, javaFile);
        return Either.left(null);
    }

    private MethodSpec buildParseField(TypeElement element, ConfigUtils.ConfigField field) {
        var parse = MethodSpec.methodBuilder("parse_" + field.name())
            .addModifiers(Modifier.PRIVATE)
            .returns(field.typeName());
        if (field.isNullable()) {
            parse.returns(field.typeName().withoutAnnotations().annotated(CommonClassNames.nullableAnnotation));
        }
        parse.addParameter(ConfigClassNames.objectValue, "config");
        var supportedType = field.mapping() == null && ConfigUtils.isSupportedType(field.typeName());
        var optionalType = field.typeName() instanceof ParameterizedTypeName ptn && ptn.rawType().equals(ConfigClassNames.optional)
            ? Optional.of(ptn.typeArguments().get(0))
            : Optional.<TypeName>empty();
        var supportedOptional = optionalType.map(ConfigUtils::isSupportedType).orElse(false);
        parse.addStatement("var value = config.get($N)", "_" + field.name() + "_path");
        if (supportedType || supportedOptional) {
            parse.beginControlFlow("if (value instanceof $T.NullValue nullValue)", ConfigClassNames.configValue);
            if (field.hasDefault()) {
                if (element.getKind().isInterface()) {
                    parse.addStatement("var defaultValue = DEFAULTS.$L()", field.name());
                } else {
                    parse.addStatement("var defaultValue = DEFAULTS.get$L()", CommonUtils.capitalize(field.name()));
                }
                if (field.typeName().isPrimitive()) {
                    parse.addStatement("return defaultValue");
                } else {
                    parse.beginControlFlow("if (defaultValue == null)");
                    if (field.isNullable()) {
                        parse.addStatement("return null");
                    } else if (optionalType.isPresent()) {
                        parse.addStatement("return $T.empty()", ConfigClassNames.optional);
                    } else {
                        parse.addStatement("throw $T.missingValue(nullValue)", ConfigClassNames.configValueException);
                    }
                    parse.nextControlFlow("else");
                    parse.addStatement("return defaultValue");
                    parse.endControlFlow();
                }
            } else {
                if (field.isNullable()) {
                    parse.addStatement("return null");
                } else if (optionalType.isPresent()) {
                    parse.addStatement("return $T.empty()", ConfigClassNames.optional);
                } else {
                    parse.addStatement("throw $T.missingValue(nullValue)", ConfigClassNames.configValueException);
                }
            }
            parse.endControlFlow();
            if (supportedOptional) {
                parse.addStatement("return $T.ofNullable($L)", Optional.class, this.parseSupportedType(optionalType.get()));
            } else {
                parse.addStatement("return $L", this.parseSupportedType(field.typeName()));
            }
        } else {
            if (field.hasDefault()) {
                parse.beginControlFlow("if (value instanceof $T.NullValue nullValue)", ConfigClassNames.configValue);
                if (element.getKind().isInterface()) {
                    parse.addStatement("var defaultValue = DEFAULTS.$L()", field.name());
                } else {
                    parse.addStatement("var defaultValue = DEFAULTS.get$L()", CommonUtils.capitalize(field.name()));
                }
                if (field.typeName().isPrimitive()) {
                    parse.addStatement("return defaultValue");
                } else {
                    parse.beginControlFlow("if (defaultValue == null)");
                    if (field.isNullable()) {
                        parse.addStatement("return null");
                    } else if (optionalType.isPresent()) {
                        parse.addStatement("return $T.empty()", ConfigClassNames.optional);
                    } else {
                        parse.addStatement("throw $T.missingValue(value)", ConfigClassNames.configValueException);
                    }
                    parse.nextControlFlow("else");
                    parse.addStatement("return defaultValue");
                    parse.endControlFlow();
                }
                parse.endControlFlow();
            } else if (field.isNullable()) {
                parse.beginControlFlow("if (value instanceof $T.NullValue nullValue)", ConfigClassNames.configValue);
                parse.addStatement("return null");
                parse.endControlFlow();
            }
            if (field.isNullable()) {
                parse.addStatement("return $L_parser.map(value)", field.name());
            } else {
                parse.addStatement("var parsed = $L_parser.map(value)", field.name());
                parse.beginControlFlow("if (parsed == null)");
                parse.addStatement("throw $T.missingValueAfterParse(value)", ConfigClassNames.configValueException);
                parse.endControlFlow();
                parse.addStatement("return parsed");
            }
        }
        return parse.build();
    }

    private MethodSpec buildConstructor(TypeSpec.Builder parser, List<ConfigUtils.ConfigField> fields) {
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var fieldFactory = new FieldFactory(this.types, elements, null, constructor, "mapper");
        for (var field : fields) {
            var isSupported = field.mapping() == null &&
                (ConfigUtils.isSupportedType(field.typeName())
                    || field.typeName() instanceof ParameterizedTypeName ptn && ptn.rawType().equals(ConfigClassNames.optional) && ConfigUtils.isSupportedType(ptn.typeArguments().get(0)));
            if (isSupported) {
                continue;
            }
            var fieldParserType = ParameterizedTypeName.get(ConfigClassNames.configValueMapper, field.typeName().box());
            var constructorParameterName = fieldFactory.add(field.mapping(), fieldParserType);
            var fieldParserName = field.name() + "_parser";
            parser.addField(fieldParserType, fieldParserName, Modifier.PRIVATE, Modifier.FINAL);
            constructor.addStatement("this.$L = $L", fieldParserName, constructorParameterName);
        }
        return constructor.build();
    }

    @Nullable
    private TypeSpec buildDefaultsType(DeclaredType type, TypeElement typeElement, List<ConfigUtils.ConfigField> fields) {
        var hasDefaults = false;
        var defaults = TypeSpec.classBuilder(typeElement.getSimpleName().toString() + "_Defaults")
            .addOriginatingElement(typeElement)
            .addAnnotation(AnnotationUtils.generated(ConfigParserGenerator.class))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(type);
        for (var tp : typeElement.getTypeParameters()) {
            defaults.addTypeVariable(TypeVariableName.get(tp));
        }
        for (var field : fields) {
            if (field.hasDefault()) {
                hasDefaults = true;
                continue;
            }
            var m = MethodSpec.methodBuilder(field.name())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(field.typeName());
            if (field.typeName() == TypeName.BOOLEAN) {
                m.addStatement("return false");
            } else if (field.typeName().isPrimitive()) {
                m.addStatement("return 0");
            } else {
                m.addStatement("return null");
            }
            defaults.addMethod(m.build());
        }

        if (hasDefaults) {
            return defaults.build();
        }
        return null;
    }

    private TypeSpec buildConfigInterfaceRecord(TypeElement typeElement, List<ConfigUtils.ConfigField> fields) {
        String implName = typeElement.getSimpleName() + "_Impl";
        var recordSpec = TypeSpec.recordBuilder(implName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(ConfigParserGenerator.class));

        var constructorBuilder = MethodSpec.constructorBuilder();
        MethodSpec.Builder constructorChecker = null;
        boolean anyFieldArray = false;

        MethodSpec.Builder equalOverrideIfArray = MethodSpec.methodBuilder("equals")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(Object.class, "o")
            .returns(boolean.class)
            .addCode("return this == o || o instanceof $L that", implName);
        for (ConfigUtils.ConfigField field : fields) {
            boolean requireCheck = !field.isNullable() && !field.typeName().isPrimitive();
            if (!anyFieldArray && field.typeName() instanceof ArrayTypeName) {
                anyFieldArray = true;
            }

            var paramType = field.typeName();
            if (requireCheck) {
                paramType = paramType.annotated(CommonClassNames.nonNullAnnotation);
            }

            var paramSpec = ParameterSpec.builder(paramType, field.name());
            constructorBuilder.addParameter(paramSpec.build());
            if (field.typeName() instanceof ArrayTypeName) {
                equalOverrideIfArray.addCode(" && $T.equals(this.$L(), that.$L())", Arrays.class, field.name(), field.name());
            } else if (field.typeName().isPrimitive()) {
                equalOverrideIfArray.addCode(" && this.$L() == that.$L()", field.name(), field.name());
            } else {
                equalOverrideIfArray.addCode(" && $T.equals(this.$L(), that.$L())", Objects.class, field.name(), field.name());
            }

            if (requireCheck) {
                if (constructorChecker == null) {
                    constructorChecker = MethodSpec.compactConstructorBuilder().addModifiers(Modifier.PUBLIC);
                }
                constructorChecker.addStatement("$T.requireNonNull($L)", Objects.class, field.name());
            }
        }

        if (constructorChecker != null) {
            recordSpec.addMethod(constructorChecker.build());
        }
        recordSpec.recordConstructor(constructorBuilder.build());
        recordSpec.addSuperinterface(typeElement.asType());

        if (anyFieldArray) {
            recordSpec.addMethod(equalOverrideIfArray.addCode(";").build());
        }

        return recordSpec.build();
    }

    private static final Map<TypeName, CodeBlock> supportedTypes = Map.ofEntries(
        Map.entry(TypeName.INT, CodeBlock.of("value.asNumber().intValue()")),
        Map.entry(TypeName.INT.box(), CodeBlock.of("value.asNumber().intValue()")),
        Map.entry(TypeName.LONG, CodeBlock.of("value.asNumber().longValue()")),
        Map.entry(TypeName.LONG.box(), CodeBlock.of("value.asNumber().longValue()")),
        Map.entry(TypeName.DOUBLE, CodeBlock.of("value.asNumber().doubleValue()")),
        Map.entry(TypeName.DOUBLE.box(), CodeBlock.of("value.asNumber().doubleValue()")),
        Map.entry(TypeName.BOOLEAN, CodeBlock.of("value.asBoolean()")),
        Map.entry(TypeName.BOOLEAN.box(), CodeBlock.of("value.asBoolean()")),
        Map.entry(ClassName.get(String.class), CodeBlock.of("value.asString()"))
    );

    private CodeBlock parseSupportedType(TypeName typeName) {
        return Objects.requireNonNull(supportedTypes.get(typeName));
    }
}
