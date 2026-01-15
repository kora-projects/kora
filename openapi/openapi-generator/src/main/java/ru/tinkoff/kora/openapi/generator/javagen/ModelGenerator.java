package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.model.ModelsMap;

import javax.lang.model.element.Modifier;
import java.util.*;

public class ModelGenerator extends AbstractJavaGenerator<ModelsMap> {
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

        return JavaFile.builder(modelPackage, type).build();
    }

    private TypeSpec buildSealed(ModelsMap ctx, CodegenModel model) {
        var b = TypeSpec.interfaceBuilder(model.classname)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.SEALED)
            .addJavadoc(Objects.requireNonNullElse(model.description, model.classname))
            .addAnnotation(Classes.json)
            .addAnnotation(AnnotationSpec.builder(Classes.jsonDiscriminatorField).addMember("value", "$S", model.discriminator.getPropertyBaseName()).build());
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
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(Objects.requireNonNullElse(model.description, "") + "\n");
        for (var field : model.allVars) {
            b.addJavadoc("@param $N $L, $L\n", field.name, Objects.requireNonNullElse(field.description, field.baseName), field.example == null ? "" : "(example: " + field.example + ")");
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
        record Field(String name, String jsonName, TypeName type, boolean required, boolean nullable) {}
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
                enumModel.isString = enumSource.isString;
                enumModel.isLong = enumSource.isLong;
                enumModel.isInteger = enumSource.isInteger;

                var enumTypeSpec = buildEnum(ctx, enumModel);
                b.addType(enumTypeSpec);
                fieldType = ClassName.get(modelPackage, model.getClassname(), enumModel.name);
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
            fields.add(new Field(field.name, field.baseName, fieldType, field.required, field.isNullable));
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

        return b.recordConstructor(constructor.build()).build();
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
        var contextModel = ctx.getModels().getFirst().getModel();
        var enumClassName = contextModel == model ? ClassName.get(modelPackage, model.name) : ClassName.get(modelPackage, contextModel.name, model.name);
        var b = TypeSpec.enumBuilder(enumClassName)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC);
        @SuppressWarnings("unchecked")
        var enumVars = (List<Map<String, Object>>) model.allowableValues.get("enumVars");
        for (var enumVar : enumVars) {
            var enumName = enumVar.get("name").toString();
            b.addEnumConstant(enumName, TypeSpec.anonymousClassBuilder("Constants.$L", enumName)
                .build());
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
        var constants = TypeSpec.classBuilder("Constants")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addAnnotation(generated());
        for (var enumVar : enumVars) {
            var enumName = enumVar.get("name").toString();
            constants.addField(FieldSpec.builder(enumValueType(model), enumName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).initializer("$L", enumVar.get("value")).build());
        }
        b.addType(constants.build());

        b.addType(TypeSpec.classBuilder("JsonWriter")
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addAnnotation(Classes.component)
            .addSuperinterface(ParameterizedTypeName.get(Classes.jsonWriter, enumClassName))
            .addField(ParameterizedTypeName.get(Classes.enumJsonWriter, enumClassName, enumValueType(model)), "delegate", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterizedTypeName.get(Classes.jsonWriter, enumValueType(model)), "delegate")
                .addStatement("this.delegate = new $T<>($T.values(), $T::getValue, delegate)", Classes.enumJsonWriter, enumClassName, enumClassName)
                .build())
            .addMethod(MethodSpec.methodBuilder("write")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(Classes.jsonGenerator, "gen")
                .addParameter(enumClassName.annotated(AnnotationSpec.builder(Classes.nullable).build()), "value")
                .addStatement("this.delegate.write(gen, value)")
                .build())
            .build());

        b.addType(TypeSpec.classBuilder("JsonReader")
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addAnnotation(Classes.component)
            .addSuperinterface(ParameterizedTypeName.get(Classes.jsonReader, enumClassName))
            .addField(ParameterizedTypeName.get(Classes.enumJsonReader, enumClassName, enumValueType(model)), "delegate", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterizedTypeName.get(Classes.jsonReader, enumValueType(model)), "delegate")
                .addStatement("this.delegate = new $T<>($T.values(), $T::getValue, delegate)", Classes.enumJsonReader, enumClassName, enumClassName)
                .build())
            .addMethod(MethodSpec.methodBuilder("read")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(Classes.jsonParser, "parser")
                .addStatement("return this.delegate.read(parser)")
                .returns(enumClassName.annotated(AnnotationSpec.builder(Classes.nullable).build()))
                .build())
            .build()
        );

        if (params.codegenMode.isClient()) {
            b.addType(TypeSpec.classBuilder("StringParameterConverter")
                .addAnnotation(generated())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(Classes.component)
                .addSuperinterface(ParameterizedTypeName.get(Classes.stringParameterConverter, enumClassName))
                .addField(ParameterizedTypeName.get(Classes.enumStringParameterConverter, enumClassName), "delegate", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("this.delegate = new $T<>($T.values(), v -> String.valueOf(v.getValue()))", Classes.enumStringParameterConverter, enumClassName)
                    .build())
                .addMethod(MethodSpec.methodBuilder("convert")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(enumClassName, "value")
                    .addStatement("return this.delegate.convert(value)")
                    .returns(String.class)
                    .build())
                .build());
        } else {
            b.addType(TypeSpec.classBuilder("StringParameterReader")
                .addAnnotation(generated())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(Classes.component)
                .addSuperinterface(ParameterizedTypeName.get(Classes.stringParameterReader, enumClassName))
                .addField(ParameterizedTypeName.get(Classes.enumStringParameterReader, enumClassName), "delegate", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("this.delegate = new $T<>($T.values(), v -> String.valueOf(v.getValue()))", Classes.enumStringParameterReader, enumClassName)
                    .build())
                .addMethod(MethodSpec.methodBuilder("read")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(String.class, "value")
                    .addStatement("return this.delegate.read(value)")
                    .returns(enumClassName)
                    .build())
                .build());
        }

        return b.build();
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

        throw new RuntimeException("Illegal enum value type: " + model);
    }
}
