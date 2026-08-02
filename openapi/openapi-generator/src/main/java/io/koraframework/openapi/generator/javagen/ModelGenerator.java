package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.model.ModelsMap;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

public class ModelGenerator extends AbstractJavaGenerator<ModelsMap> {
    private record Field(String name, String jsonName, TypeName type, boolean required, boolean nullable, String description, String defaultValue, String example) {}

    @Override
    public JavaFile generate(ModelsMap ctx) {
        var models = ctx.getModels();
        if (models.size() != 1) {
            throw new IllegalArgumentException();
        }
        var model = models.getFirst().getModel();
        var type = (TypeSpec) null;
        if (model.isEnum) {
            type = buildEnum(ctx, model);
        } else if (model.discriminator != null) {
            type = buildSealed(ctx, model);
        } else {
            type = buildRecord(ctx, model);
        }

        writeEnumMapperModules(ctx);

        return JavaFile.builder(modelPackage, type).build();
    }

    private TypeSpec buildSealed(ModelsMap ctx, CodegenModel model) {
        var b = TypeSpec.interfaceBuilder(model.classname)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.SEALED)
            .addJavadoc(Objects.requireNonNullElse(model.description, model.classname))
            .addAnnotation(Classes.json)
            .addAnnotation(AnnotationSpec.builder(Classes.jsonDiscriminatorField).addMember("value", "$S", model.discriminator.getPropertyBaseName()).build());
        buildAdditionalModelTypeAnnotations().forEach(b::addAnnotation);
        if (params.enableValidation) {
            b.addAnnotation(Classes.valid);
        }
        var permittedSubclasses = new HashSet<ClassName>();
        for (var mappedModel : model.discriminator.getMappedModels()) {
            permittedSubclasses.add((ClassName) asType(mappedModel.getModel()));
        }
        b.addPermittedSubclasses(permittedSubclasses);
        for (var field : model.allVars) {
            var type = fieldType(field);
            var m = MethodSpec.methodBuilder(field.name)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(type);
            if (field.description != null) {
                m.addJavadoc(field.description);
            }
            b.addMethod(m.build());
        }
        return b.build();
    }

    private TypeSpec buildRecord(ModelsMap ctx, CodegenModel model) {
        var b = TypeSpec.recordBuilder(model.getClassname())
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC);
        buildAdditionalModelTypeAnnotations().forEach(b::addAnnotation);
        if (model.description == null || model.description.isBlank()) {
            b.addJavadoc("$L\n", model.classname);
        } else {
            b.addJavadoc("$L - $L\n", model.classname, model.description);
        }
        for (var field : model.allVars) {
            b.addJavadoc("@param $N $L$L\n", field.name, Objects.requireNonNullElse(field.description, field.baseName), fieldJavadocMetadata(field));
        }
        if (params.enableValidation) {
            b.addAnnotation(Classes.valid);
        }
        b.addAnnotation(Classes.jsonWriterAnnotation);
        var superinterfaces = new HashSet<ClassName>();
        var discriminatorFields = new HashSet<String>();
        var discriminatorValues = new HashSet<String>();
        var parentFields = new HashMap<String, CodegenProperty>();
        if (model.getComposedSchemas() != null && model.getComposedSchemas().getAllOf() != null) {
            for (var codegenProperty : model.getComposedSchemas().getAllOf()) {
                if (codegenProperty.isModel) {
                    for (var var : codegenProperty.vars) {
                        if (!var.isAnyType) {
                            parentFields.put(var.name, var);
                        }
                    }
                }
            }
        }
        for (var entry : models.entrySet()) {
            var m = entry.getValue().getModels().getFirst().getModel();
            if (m.getComposedSchemas() != null && m.getComposedSchemas().getOneOf() != null) {
                var isSuper = false;
                for (var codegenProperty : m.getComposedSchemas().getOneOf()) {
                    if (codegenProperty.getDataType() != null && codegenProperty.getDataType().equals(model.getDataType())) {
                        superinterfaces.add((ClassName) asType(m));
                        isSuper = true;
                        break;
                    }
                }
                if (isSuper) {
                    for (var prop : m.allVars) {
                        parentFields.put(prop.name, prop);
                    }
                }
            }
            if (m.discriminator != null) {
                var isSuper = false;
                for (var mappedModel : m.discriminator.getMappedModels()) {
                    if (mappedModel.getModelName().equals(model.name)) {
                        superinterfaces.add((ClassName) asType(m));
                        discriminatorFields.add(m.discriminator.getPropertyName());
                        discriminatorValues.add(mappedModel.getMappingName());
                        var parentDiscriminatorField = m.allVars.stream()
                            .filter(p -> p.name.equals(m.discriminator.getPropertyName()))
                            .findFirst()
                            .orElse(null);
                        isSuper = true;
                        if (parentDiscriminatorField != null) {
                            if (model.allVars.stream().noneMatch(p -> p.name.equals(parentDiscriminatorField.name))) {
                                var field = parentDiscriminatorField.clone();
                                field.isOverridden = true;
                                model.allVars.add(field);
                                model.requiredVars.add(field);
                                parentFields.put(field.name, field);
                            }
                        }
                    }
                }
                if (isSuper) {
                    for (var prop : m.allVars) {
                        parentFields.put(prop.name, prop);
                    }
                }
            }
        }
        if (discriminatorFields.size() > 1) {
            throw new IllegalArgumentException("Multiple discriminator fields is not supported");
        }
        if (!discriminatorFields.isEmpty()) {
            b.addAnnotation(AnnotationSpec.builder(Classes.jsonDiscriminatorValue).addMember("value", "$L", discriminatorValues.stream().map(s -> CodeBlock.of("$S", s)).collect(CodeBlock.joining(", ", "{", "}"))).build());
        }
        b.addSuperinterfaces(superinterfaces);
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var fields = new ArrayList<Field>();
        for (var field : model.allVars) {
            if (parentFields.containsKey(field.name)) {
                var parentField = parentFields.get(field.name);
                if (parentField.required) {
                    field.required = true;
                }
            }
            if (field.isAnyType) {
                var parentFieldMaybe = parentFields.get(field.name);
                if (parentFieldMaybe != null) {
                    field = parentFieldMaybe;
                }
            }
            var fieldType = fieldType(field);
            if (field.isInnerEnum) {
                // todo this field may be inherited from interface model and we should not generate enum here for those cases, but that's some weird contract design tbh
                var enumModel = new CodegenModel();
                var enumSource = field;
                if (field.isContainer) {
                    enumSource = field.items;
                }
                enumModel.name = enumSource.enumName;
                enumModel.allowableValues = enumSource.allowableValues;
                enumModel.dataType = enumSource.dataType;
                enumModel.description = enumSource.description;
                enumModel.vendorExtensions = enumSource.vendorExtensions;
                enumModel.isString = enumSource.isString;
                enumModel.isLong = enumSource.isLong;
                enumModel.isInteger = enumSource.isInteger;

                var enumClassName = ClassName.get(modelPackage, model.getClassname(), enumModel.name);
                var enumTypeSpec = buildEnum(enumModel);
                b.addType(enumTypeSpec);
                fieldType = enumClassName;
                if (field.isNullable && !field.required) {
                    fieldType = ParameterizedTypeName.get(Classes.jsonNullable, fieldType);
                } else if (field.isNullable || !field.required) {
                    fieldType = fieldType.annotated(AnnotationSpec.builder(Classes.nullable).build());
                }
            }
            var p = ParameterSpec.builder(fieldType, field.name);
            if (!field.name.equals(field.baseName)) {
                p.addAnnotation(AnnotationSpec.builder(Classes.jsonField).addMember("value", "$S", field.baseName).build());
            }
            var validation = getValidation(field);
            if (validation != null) {
                p.addAnnotation(validation);
            }
            fields.add(new Field(field.name, field.baseName, fieldType, field.required, field.isNullable, field.description, field.defaultValue, field.example));
            if (field.required && field.isNullable) {
                p.addAnnotation(AnnotationSpec.builder(Classes.jsonInclude).addMember("value", "$T.ALWAYS", Classes.jsonInclude.nestedClass("IncludeType")).build());
            }
            constructor.addParameter(p.build());
        }
        if (fields.stream().anyMatch(f -> f.required && f.nullable)) {
            b.addMethod(MethodSpec.compactConstructorBuilder().addModifiers(Modifier.PUBLIC).build());
        } else {
            b.addMethod(MethodSpec.compactConstructorBuilder().addModifiers(Modifier.PUBLIC).addAnnotation(Classes.jsonReaderAnnotation).build());
        }
        if (fields.stream().anyMatch(f -> !f.required)) {
            var c = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addCode("this(");
            for (var i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    c.addCode(", ");
                }
                var f = fields.get(i);
                if (f.required) {
                    c.addParameter(f.type, f.name);
                    c.addCode("$N", f.name);
                } else {
                    c.addCode("null");
                }
            }
            c.addCode(");\n");
            b.addMethod(c.build());
        }
        if (fields.stream().anyMatch(f -> f.required && f.nullable)) {
            var c = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Classes.jsonReaderAnnotation);
            for (var f : fields) {
                if (f.required && f.nullable) {
                    c.beginControlFlow("if (!$N.isDefined())", f.name)
                        .addStatement("throw new IllegalArgumentException($S)", "Field '%s' was not found in parsed json".formatted(f.name))
                        .endControlFlow();
                }
            }
            c.addCode("this(");
            for (var i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    c.addCode(", ");
                }
                var f = fields.get(i);
                var jsonField = AnnotationSpec.builder(Classes.jsonField).addMember("value", "$S", f.jsonName).build();
                var annotations = !f.jsonName.equals(f.name) ? List.of(jsonField) : List.<AnnotationSpec>of();
                if (f.required && f.nullable) {
                    c.addParameter(ParameterSpec.builder(ParameterizedTypeName.get(Classes.jsonNullable, f.type.withoutAnnotations()), f.name)
                        .addAnnotations(annotations)
                        .build());
                    c.addCode("$N.value()", f.name);
                } else {
                    c.addParameter(ParameterSpec.builder(f.type, f.name).addAnnotations(annotations).build());
                    c.addCode("$N", f.name);
                }
            }
            c.addCode(");\n");
            b.addMethod(c.build());
        }
        for (var field : fields) {
            b.addMethod(buildWithMethod(model, fields, field));
        }

        return b.recordConstructor(constructor.build()).build();
    }

    private MethodSpec buildWithMethod(CodegenModel model, List<Field> fields, Field field) {
        var method = MethodSpec.methodBuilder("with" + capitalize(field.name()))
            .addModifiers(Modifier.PUBLIC)
            .addParameter(field.type(), field.name())
            .returns(ClassName.get(modelPackage, model.getClassname()));
        buildWithMethodJavadoc(method, field);
        method.addCode("if ($L) return this; ", fieldEquals(field));
        method.addCode("return new $T(", ClassName.get(modelPackage, model.getClassname()));
        for (var i = 0; i < fields.size(); i++) {
            if (i > 0) {
                method.addCode(", ");
            }
            var current = fields.get(i);
            method.addCode("$N", current.name().equals(field.name()) ? field.name() : "this." + current.name());
        }
        method.addCode(");\n");
        return method.build();
    }

    private String fieldJavadocMetadata(CodegenProperty field) {
        var metadata = new ArrayList<String>();
        if (field.defaultValue != null) {
            metadata.add("default: " + field.defaultValue);
        }
        if (field.example != null) {
            metadata.add("example: " + field.example);
        }
        return metadata.isEmpty() ? "" : " (" + String.join(", ", metadata) + ")";
    }

    private void buildWithMethodJavadoc(MethodSpec.Builder method, Field field) {
        var javadoc = new ArrayList<String>();
        if (field.description() != null && !field.description().equals(field.jsonName()) && !field.description().equals(field.name())) {
            javadoc.add(field.description());
        }
        if (field.defaultValue() != null) {
            javadoc.add("default: " + field.defaultValue());
        }
        if (field.example() != null) {
            javadoc.add("example: " + field.example());
        }
        if (!javadoc.isEmpty()) {
            method.addJavadoc("($L)", String.join(", ", javadoc));
        }
    }

    private CodeBlock fieldEquals(Field field) {
        var type = field.type().withoutAnnotations();
        if (type.equals(TypeName.FLOAT)) {
            return CodeBlock.of("$T.compare(this.$N, $N) == 0", Float.class, field.name(), field.name());
        }
        if (type.equals(TypeName.DOUBLE)) {
            return CodeBlock.of("$T.compare(this.$N, $N) == 0", Double.class, field.name(), field.name());
        }
        if (type.isPrimitive()) {
            return CodeBlock.of("this.$N == $N", field.name(), field.name());
        }
        return CodeBlock.of("$T.equals(this.$N, $N)", Objects.class, field.name(), field.name());
    }

    private TypeName fieldType(CodegenProperty field) {
        var type = asType(field);
        if (field.isNullable && !field.required) {
            return ParameterizedTypeName.get(Classes.jsonNullable, type.box());
        } else if (!field.required || field.isNullable) {
            return type.box().annotated(AnnotationSpec.builder(Classes.nullable).build());
        } else {
            return type;
        }
    }

    private TypeSpec buildEnum(ModelsMap ctx, CodegenModel model) {
        return buildEnum(model);
    }

    private TypeSpec buildEnum(CodegenModel model) {
        var b = TypeSpec.enumBuilder(model.name)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC);
        buildAdditionalEnumTypeAnnotations().forEach(b::addAnnotation);
        if (model.description != null && !model.description.isBlank()) {
            b.addJavadoc("$L\n", model.description);
        }
        @SuppressWarnings("unchecked")
        var enumVars = (List<Map<String, Object>>) model.allowableValues.get("enumVars");
        for (var i = 0; i < enumVars.size(); i++) {
            var enumVar = enumVars.get(i);
            var enumName = enumVar.get("name").toString();
            var enumConstant = TypeSpec.anonymousClassBuilder("$L", enumVar.get("value"));
            var description = enumValueDescription(model, enumVar, i);
            if (description != null && !description.isBlank()) {
                enumConstant.addJavadoc("$L\n", description);
            }
            b.addEnumConstant(enumName, enumConstant.build());
        }
        b.addField(enumValueType(model), "value", Modifier.PRIVATE, Modifier.FINAL);
        b.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(enumValueType(model), "value")
            .addStatement("this.value = value")
            .build());
        b.addMethod(MethodSpec.methodBuilder("getValue")
            .returns(enumValueType(model))
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return this.value")
            .build());
        b.addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return String.valueOf(value)")
            .build());
        return b.build();
    }

    private void writeEnumMapperModules(ModelsMap ctx) {
        var model = ctx.getModels().getFirst().getModel();
        var modules = new LinkedHashMap<String, JavaFile>();
        if (model.isEnum) {
            var enumClassName = ClassName.get(modelPackage, model.name);
            modules.put(enumMapperModuleName(enumClassName), buildEnumMapperModuleFile(enumClassName, model));
        }
        if (model.discriminator != null) {
            return;
        }
        for (var field : model.allVars) {
            if (!field.isInnerEnum) {
                continue;
            }
            var enumModel = enumModel(field);
            var enumClassName = ClassName.get(modelPackage, model.getClassname(), enumModel.name);
            modules.put(enumMapperModuleName(enumClassName), buildEnumMapperModuleFile(enumClassName, enumModel));
        }
        for (var module : modules.values()) {
            try {
                module.writeTo(Path.of(outputFolder));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private CodegenModel enumModel(CodegenProperty field) {
        var enumModel = new CodegenModel();
        var enumSource = field;
        if (field.isContainer) {
            enumSource = field.items;
        }
        enumModel.name = enumSource.enumName;
        enumModel.allowableValues = enumSource.allowableValues;
        enumModel.dataType = enumSource.dataType;
        enumModel.description = enumSource.description;
        enumModel.vendorExtensions = enumSource.vendorExtensions;
        enumModel.isString = enumSource.isString;
        enumModel.isLong = enumSource.isLong;
        enumModel.isInteger = enumSource.isInteger;
        return enumModel;
    }

    private String enumValueDescription(CodegenModel model, Map<String, Object> enumVar, int index) {
        var enumDescription = enumVar.get("description");
        if (enumDescription != null) {
            return enumDescription.toString();
        }
        enumDescription = enumVar.get("enumDescription");
        if (enumDescription != null) {
            return enumDescription.toString();
        }
        if (model.vendorExtensions == null) {
            return null;
        }
        for (var extension : List.of("x-enum-descriptions", "x-enumDescriptions", "x-enum-var-descriptions")) {
            var descriptions = model.vendorExtensions.get(extension);
            var description = enumValueDescription(descriptions, enumVar, index);
            if (description != null) {
                return description;
            }
        }
        return null;
    }

    private String enumValueDescription(Object descriptions, Map<String, Object> enumVar, int index) {
        if (descriptions instanceof List<?> list) {
            if (index >= list.size()) {
                return null;
            }
            var description = list.get(index);
            return description == null ? null : description.toString();
        }
        if (descriptions instanceof Map<?, ?> map) {
            var description = map.get(enumVar.get("value"));
            if (description == null) {
                description = map.get(enumVar.get("name"));
            }
            return description == null ? null : description.toString();
        }
        return null;
    }

    private JavaFile buildEnumMapperModuleFile(ClassName enumClassName, CodegenModel model) {
        var module = buildEnumMapperModule(enumMapperModuleName(enumClassName), enumClassName, model);
        return JavaFile.builder(modelPackage, module).build();
    }

    private String enumMapperModuleName(ClassName enumClassName) {
        return "$" + String.join("", enumClassName.simpleNames()) + "MapperModule";
    }

    private TypeSpec buildEnumMapperModule(String moduleName, ClassName enumClassName, CodegenModel model) {
        var enumValueType = enumValueType(model);
        var enumSimpleName = enumClassName.simpleName();
        var module = TypeSpec.interfaceBuilder(moduleName)
            .addAnnotation(generated())
            .addAnnotation(Classes.module)
            .addModifiers(Modifier.PUBLIC);

        var jsonWriter = MethodSpec.methodBuilder(StringUtils.uncapitalize(enumSimpleName) + "JsonWriter")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(Classes.defaultComponent)
            .returns(ParameterizedTypeName.get(Classes.jsonWriter, enumClassName));
        if (isInlineEnumJsonValueType(enumValueType)) {
            jsonWriter.addStatement("return new $T<>($T.values(), $T::getValue, $L)", Classes.enumJsonWriter, enumClassName, enumClassName, enumJsonWriter(enumValueType));
        } else {
            jsonWriter
                .addParameter(ParameterizedTypeName.get(Classes.jsonWriter, enumValueType), "delegate")
                .addStatement("return new $T<>($T.values(), $T::getValue, delegate)", Classes.enumJsonWriter, enumClassName, enumClassName);
        }
        module.addMethod(jsonWriter.build());

        var jsonReader = MethodSpec.methodBuilder(StringUtils.uncapitalize(enumSimpleName) + "JsonReader")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(Classes.defaultComponent)
            .returns(ParameterizedTypeName.get(Classes.jsonReader, enumClassName));
        if (isInlineEnumJsonValueType(enumValueType)) {
            jsonReader.addStatement("return new $T<>($T.values(), $T::getValue, $L)", Classes.enumJsonReader, enumClassName, enumClassName, enumJsonReader(enumValueType));
        } else {
            jsonReader
                .addParameter(ParameterizedTypeName.get(Classes.jsonReader, enumValueType), "delegate")
                .addStatement("return new $T<>($T.values(), $T::getValue, delegate)", Classes.enumJsonReader, enumClassName, enumClassName);
        }
        module.addMethod(jsonReader.build());

        if (params.codegenMode.isClient()) {
            module.addMethod(MethodSpec.methodBuilder(StringUtils.uncapitalize(enumSimpleName) + "StringParameterConverter")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(Classes.defaultComponent)
                .returns(ParameterizedTypeName.get(Classes.stringParameterConverter, enumClassName))
                .addStatement("return new $T<>($T.values(), v -> String.valueOf(v.getValue()))", Classes.enumStringParameterConverter, enumClassName)
                .build());
        } else {
            module.addMethod(MethodSpec.methodBuilder(StringUtils.uncapitalize(enumSimpleName) + "StringParameterReader")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(Classes.defaultComponent)
                .returns(ParameterizedTypeName.get(Classes.stringParameterReader, enumClassName))
                .addStatement("return new $T<>($T.values(), v -> String.valueOf(v.getValue()))", Classes.enumStringParameterReader, enumClassName)
                .build());
        }

        return module.build();
    }

    private boolean isInlineEnumJsonValueType(TypeName enumValueType) {
        return enumValueType.equals(ClassName.get(String.class))
               || enumValueType.equals(TypeName.INT.box())
               || enumValueType.equals(TypeName.LONG.box());
    }

    private CodeBlock enumJsonWriter(TypeName enumValueType) {
        if (enumValueType.equals(ClassName.get(String.class))) {
            return CodeBlock.of("(gen, object) -> {\n"
                                + "  if (object == null) {\n"
                                + "    gen.writeNull();\n"
                                + "  } else {\n"
                                + "    gen.writeString(object);\n"
                                + "  }\n"
                                + "}");
        }
        if (enumValueType.equals(TypeName.INT.box()) || enumValueType.equals(TypeName.LONG.box())) {
            return CodeBlock.of("(gen, object) -> {\n"
                                + "  if (object == null) {\n"
                                + "    gen.writeNull();\n"
                                + "  } else {\n"
                                + "    gen.writeNumber(object);\n"
                                + "  }\n"
                                + "}");
        }
        throw new RuntimeException("Illegal enum value type: " + enumValueType);
    }

    private CodeBlock enumJsonReader(TypeName enumValueType) {
        var streamReadException = ClassName.get("tools.jackson.core.exc", "StreamReadException");
        if (enumValueType.equals(ClassName.get(String.class))) {
            return CodeBlock.of("parser -> switch (parser.currentToken()) {\n"
                                + "  case VALUE_NULL -> null;\n"
                                + "  case VALUE_STRING -> parser.getString();\n"
                                + "  default -> throw new $T(parser, $S + parser.currentToken());\n"
                                + "}", streamReadException, "Expecting VALUE_STRING token, got ");
        }
        if (enumValueType.equals(TypeName.INT.box())) {
            return CodeBlock.of("parser -> switch (parser.currentToken()) {\n"
                                + "  case VALUE_NULL -> null;\n"
                                + "  case VALUE_NUMBER_INT -> parser.getIntValue();\n"
                                + "  default -> throw new $T(parser, $S + parser.currentToken());\n"
                                + "}", streamReadException, "Expecting VALUE_NUMBER_INT token, got ");
        }
        if (enumValueType.equals(TypeName.LONG.box())) {
            return CodeBlock.of("parser -> switch (parser.currentToken()) {\n"
                                + "  case VALUE_NULL -> null;\n"
                                + "  case VALUE_NUMBER_INT -> parser.getLongValue();\n"
                                + "  default -> throw new $T(parser, $S + parser.currentToken());\n"
                                + "}", streamReadException, "Expecting VALUE_NUMBER_INT token, got ");
        }
        throw new RuntimeException("Illegal enum value type: " + enumValueType);
    }

    private TypeName enumValueType(CodegenModel model) {
        if (model.isString) {
            return ClassName.get(String.class);
        }
        if (model.isLong || "Long".equals(model.dataType)) {
            return TypeName.LONG.box();
        }
        if (model.isInteger || "Integer".equals(model.dataType)) {
            return TypeName.INT.box();
        }
        if ("Boolean".equals(model.dataType)) {
            return TypeName.BOOLEAN.box();
        }
        if ("Float".equals(model.dataType)) {
            return TypeName.FLOAT.box();
        }
        if ("Double".equals(model.dataType)) {
            return TypeName.DOUBLE.box();
        }
        if ("BigDecimal".equals(model.dataType)) {
            return ClassName.get(BigDecimal.class);
        }
        if (model.dataType != null && !model.dataType.isBlank()) {
            return ClassName.bestGuess(model.dataType);
        }

        return ClassName.get(Object.class);
    }
}
