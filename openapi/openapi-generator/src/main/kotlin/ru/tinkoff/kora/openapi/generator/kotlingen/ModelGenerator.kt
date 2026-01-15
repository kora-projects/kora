package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenModel
import org.openapitools.codegen.CodegenProperty
import org.openapitools.codegen.model.ModelsMap


class ModelGenerator : AbstractKotlinGenerator<ModelsMap>() {
    override fun generate(ctx: ModelsMap): FileSpec {
        val model = ctx.models.single().model
        val type = when {
            model.isEnum -> buildEnum(ctx, model)
            model.discriminator != null -> buildSealed(ctx, model)
            else -> buildRecord(ctx, model)
        }
        return FileSpec.get(modelPackage, type)
    }

    private fun buildRecord(ctx: ModelsMap, model: CodegenModel): TypeSpec {
        val b = TypeSpec.classBuilder(model.classname)
        if (model.allVars.isNullOrEmpty()) {
            return b.build()
        }
        b.addModifiers(KModifier.DATA)
        for (field in model.allVars) {
            b.addKdoc("@param %N %L, %L\n", field.name, field.description ?: field.baseName, if (field.example == null) "" else "(example: " + field.example + ")")
        }
        if (params.enableValidation) {
            b.addAnnotation(Classes.valid.asKt())
        }
        b.addAnnotation(Classes.json.asKt())
        val superinterfaces = mutableSetOf<ClassName>()
        val discriminatorFields = mutableSetOf<String>()
        val discriminatorValues = mutableSetOf<String>()
        val superInterfaceFields = mutableMapOf<String, CodegenProperty>()
        val superModelFields = mutableMapOf<String, CodegenProperty>()
        if (model.composedSchemas?.allOf != null) {
            for (codegenProperty in model.composedSchemas.allOf) {
                if (codegenProperty.isModel) {
                    for (variable in codegenProperty.vars) {
                        if (!variable.isAnyType) {
                            superModelFields[variable.name] = variable
                        }
                    }
                }
            }
        }
        for (entry in models.entries) {
            val m = entry.value.models.single().model
            m.composedSchemas?.oneOf?.let {
                var isSuper = false
                for (codegenProperty in it) {
                    if (codegenProperty.getDataType() != null && codegenProperty.getDataType() == model.getDataType()) {
                        superinterfaces.add(asType(m).asKt() as ClassName)
                        isSuper = true
                        break
                    }
                }
                if (isSuper) {
                    for (prop in m.allVars) {
                        superInterfaceFields[prop.name] = prop
                    }
                }
            }
            if (m.discriminator != null) {
                var isSuper = false
                for (mappedModel in m.discriminator.mappedModels) {
                    if (mappedModel.modelName == model.name) {
                        superinterfaces.add(asType(m).asKt() as ClassName)
                        discriminatorFields.add(m.discriminator.propertyName)
                        discriminatorValues.add(mappedModel.mappingName)
                        isSuper = true
                        val parentDiscriminatorField = m.allVars.firstOrNull { p -> p.name.equals(m.discriminator.propertyName) }
                        if (parentDiscriminatorField != null) {
                            if (model.allVars.none { p -> p.name.equals(parentDiscriminatorField.name) }) {
                                val field = parentDiscriminatorField.clone()
                                field.isOverridden = true
                                model.allVars.add(field)
                                model.requiredVars.add(field)
                                superInterfaceFields[field.name] = field
                            }
                        }
                    }
                }
                if (isSuper) {
                    for (prop in m.allVars) {
                        superModelFields[prop.name] = prop
                        superInterfaceFields[prop.name] = prop
                    }
                }
            }
        }
        if (discriminatorFields.size > 1) {
            throw IllegalArgumentException("Multiple discriminator fields is not supported")
        }
        if (!discriminatorFields.isEmpty()) {
            b.addAnnotation(
                AnnotationSpec.builder(Classes.jsonDiscriminatorValue.asKt())
                    .addMember("value = %L", discriminatorValues.map { d -> CodeBlock.of("%S", d) }.joinToCode(", ", "[", "]")).build()
            )
        }
        b.addSuperinterfaces(superinterfaces)
        val constructor = FunSpec.constructorBuilder()

        data class Field(val name: String, val jsonName: String, val type: TypeName, val required: Boolean, val nullable: Boolean)

        val fields = mutableListOf<Field>()
        for (f in model.allVars) {
            var field = f
            superInterfaceFields[field.name]?.let {
                if (it.required) {
                    field.required = true
                }
            }
            if (field.isAnyType) {
                var parentFieldMaybe = superModelFields[field.name]
                if (parentFieldMaybe == null) {
                    parentFieldMaybe = superModelFields[field.name]
                }
                if (parentFieldMaybe != null) {
                    field = parentFieldMaybe
                }
            }
            var fieldType = fieldType(field)
            if (field.isInnerEnum) {
                // todo this field may be inherited from interface model and we should not generate enum here for those cases, but that's some weird contract design tbh
                val enumModel = CodegenModel()
                var enumSource = field
                if (field.isContainer) {
                    enumSource = field.items
                }
                enumModel.name = enumSource.enumName
                enumModel.allowableValues = enumSource.allowableValues
                enumModel.dataType = enumSource.dataType
                enumModel.isString = enumSource.isString
                enumModel.isLong = enumSource.isLong
                enumModel.isInteger = enumSource.isInteger
                val enumTypeSpec = buildEnum(ctx, enumModel)
                b.addType(enumTypeSpec)
                fieldType = ClassName(modelPackage, model.getClassname(), enumModel.name)
                if (field.isNullable && !field.required) {
                    fieldType = Classes.jsonNullable.asKt().parameterizedBy(fieldType)
                } else if (!field.isNullable && !field.required) {
                    fieldType = fieldType.copy(true)
                }
            }
            val p = ParameterSpec.builder(field.name, fieldType)
            if (field.name != field.baseName) {
                p.addAnnotation(AnnotationSpec.builder(Classes.jsonField.asKt()).useSiteTarget(AnnotationSpec.UseSiteTarget.PROPERTY).addMember("value = %S", field.baseName).build())
                p.addAnnotation(AnnotationSpec.builder(Classes.jsonField.asKt()).useSiteTarget(AnnotationSpec.UseSiteTarget.PARAM).addMember("value = %S", field.baseName).build())
            }
            if (params.enableValidation) {
                getValidation(field)?.let { p.addAnnotation(it) }
            }
            if (field.isNullable) {
                if (field.required) {
                    p.defaultValue("null")
                } else {
                    p.defaultValue("%T.nullValue()", Classes.jsonNullable.asKt())
                }
            } else if (!field.required) {
                p.defaultValue("null")
            } else if (field.defaultValue != null) {
                p.defaultValue(field.defaultValue)
            }
            fields.add(Field(field.name, field.baseName, fieldType, field.required, fieldType.isNullable))
            constructor.addParameter(p.build())
            if (field.required && field.isNullable) {
                p.addAnnotation(AnnotationSpec.builder(Classes.jsonInclude.asKt()).addMember("value = %T.ALWAYS", Classes.jsonInclude.nestedClass("IncludeType").asKt()).build())
            }
            val prop = PropertySpec.builder(field.name, fieldType).initializer(field.name)
            if (superInterfaceFields.contains(field.name)) {
                prop.addModifiers(KModifier.OVERRIDE)
            }
            b.addProperty(prop.build())
        }
        b.primaryConstructor(constructor.build())
        if (fields.any { it.required && it.nullable }) {
            val c = FunSpec.constructorBuilder()
                .addAnnotation(Classes.jsonReaderAnnotation.asKt())
            val args = mutableListOf<CodeBlock>()
            for (f in fields) {
                val jsonField = if (f.jsonName != f.name) AnnotationSpec.builder(Classes.jsonField.asKt()).addMember("value = %S", f.jsonName).build() else null
                if (f.required && f.nullable) {
                    c.addParameter(
                        ParameterSpec.builder(f.name, Classes.jsonNullable.asKt().parameterizedBy(f.type))
                            .addAnnotations(listOfNotNull(jsonField))
                            .build()
                    )
                    args.add(CodeBlock.of("\n  %N. let { if (it.isDefined) it.value() else throw IllegalArgumentException(%S) }", f.name, "Field '${f.name}' was not found in parsed json"))
                } else {
                    c.addParameter(ParameterSpec.builder(f.name, f.type).addAnnotations(listOfNotNull(jsonField)).build())
                    args.add(CodeBlock.of("\n  %N", f.name))
                }
            }
            c.callThisConstructor(args)
            b.addFunction(c.build())
        }
        return b.build()
    }

    private fun buildSealed(ctx: ModelsMap, model: CodegenModel): TypeSpec {
        val b = TypeSpec.interfaceBuilder(model.classname)
            .addModifiers(KModifier.SEALED)
            .addAnnotation(generated())
            .addKdoc(model.description ?: model.classname)
            .addAnnotation(Classes.json.asKt())
            .addAnnotation(
                AnnotationSpec.builder(Classes.jsonDiscriminatorField.asKt())
                    .addMember("value = %S", model.discriminator.propertyBaseName)
                    .build()
            )
        if (params.enableValidation) {
            b.addAnnotation(Classes.valid.asKt())
        }

        for (field in model.allVars) {
            val type = fieldType(field)
            val prop = PropertySpec.builder(field.name, type, KModifier.OPEN)
            field.description?.let {
                prop.addKdoc(it)
            }
            b.addProperty(prop.build())
        }

        return b.build()
    }

    private fun buildEnum(ctx: ModelsMap, model: CodegenModel): TypeSpec {
        val contextModel = ctx.models.first().model
        val enumClassName = if (contextModel == model)
            ClassName(modelPackage, model.name)
        else
            ClassName(modelPackage, contextModel.name, model.name)
        val b = TypeSpec.enumBuilder(enumClassName)
            .addAnnotation(generated())
        val enumVars = model.allowableValues["enumVars"] as List<Map<String, Any>>
        for (enumVar in enumVars) {
            val enumName = enumVar["name"].toString()
            b.addEnumConstant(
                enumName, TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("Constants.%L", enumName)
                    .build()
            )
        }
        b.addProperty(PropertySpec.builder("value", enumValueType(model)).initializer("value").build())
        b.primaryConstructor(
            FunSpec.constructorBuilder()
                .addModifiers(KModifier.PRIVATE)
                .addParameter("value", enumValueType(model))
                .build()
        )
        b.addFunction(
            FunSpec.builder("toString")
                .addModifiers(KModifier.OVERRIDE)
                .returns(String::class)
                .addStatement("return value.toString()")
                .build()
        )
        val constants = TypeSpec.objectBuilder("Constants")
            .addAnnotation(generated())
        for (enumVar in enumVars) {
            val enumName = enumVar["name"].toString()
            constants.addProperty(PropertySpec.builder(enumName, enumValueType(model), KModifier.CONST).initializer("%L", enumVar["value"]).build())
        }
        b.addType(constants.build())

        b.addType(
            TypeSpec.classBuilder("JsonWriter")
                .addAnnotation(generated())
                .addAnnotation(Classes.component.asKt())
                .addSuperinterface(Classes.jsonWriter.asKt().parameterizedBy(enumClassName))
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("delegate", Classes.jsonWriter.asKt().parameterizedBy(enumValueType(model)))
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("delegate", Classes.enumJsonWriter.asKt().parameterizedBy(enumClassName, enumValueType(model)))
                        .initializer("%T(entries.toTypedArray(), %T::value, delegate)", Classes.enumJsonWriter.asKt(), enumClassName)
                        .build()
                )
                .addFunction(
                    FunSpec.builder("write")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("gen", Classes.jsonGenerator.asKt())
                        .addParameter("value", enumClassName.copy(true))
                        .addStatement("this.delegate.write(gen, value)")
                        .build()
                )
                .build()
        )
        b.addType(
            TypeSpec.classBuilder("JsonReader")
                .addAnnotation(generated())
                .addAnnotation(Classes.component.asKt())
                .addSuperinterface(Classes.jsonReader.asKt().parameterizedBy(enumClassName))
                .addProperty(
                    PropertySpec.builder("delegate", Classes.enumJsonReader.asKt().parameterizedBy(enumClassName, enumValueType(model)))
                        .initializer("%T(entries.toTypedArray(), %T::value, delegate)", Classes.enumJsonReader.asKt(), enumClassName)
                        .build()
                )
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("delegate", Classes.jsonReader.asKt().parameterizedBy(enumValueType(model)))
                        .build()
                )
                .addFunction(
                    FunSpec.builder("read")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("parser", Classes.jsonParser.asKt())
                        .addStatement("return this.delegate.read(parser)")
                        .returns(enumClassName.copy(true))
                        .build()
                )
                .build()
        )

        if (params.codegenMode.isClient()) {
            b.addType(
                TypeSpec.classBuilder("StringParameterConverter")
                    .addAnnotation(generated())
                    .addAnnotation(Classes.component.asKt())
                    .addSuperinterface(Classes.stringParameterConverter.asKt().parameterizedBy(enumClassName))
                    .addProperty(
                        PropertySpec.builder("delegate", Classes.enumStringParameterConverter.asKt().parameterizedBy(enumClassName))
                            .initializer("%T(entries.toTypedArray()) { it.value.toString() }", Classes.enumStringParameterConverter.asKt())
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("convert")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("value", enumClassName)
                            .addStatement("return this.delegate.convert(value)")
                            .returns(String::class)
                            .build()
                    )
                    .build()
            )
        } else {
            b.addType(
                TypeSpec.classBuilder("StringParameterReader")
                    .addAnnotation(generated())
                    .addAnnotation(Classes.component.asKt())
                    .addSuperinterface(Classes.stringParameterReader.asKt().parameterizedBy(enumClassName))
                    .addProperty(
                        PropertySpec.builder("delegate", Classes.enumStringParameterReader.asKt().parameterizedBy(enumClassName))
                            .initializer("%T(entries.toTypedArray()) { it.value.toString() }", Classes.enumStringParameterReader.asKt())
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("read")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("value", String::class)
                            .addStatement("return this.delegate.read(value)")
                            .returns(enumClassName)
                            .build()
                    )
                    .build()
            )
        }
        return b.build()
    }

    private fun fieldType(field: CodegenProperty): TypeName {
        val type = asType(field).asKt()
        return when {
            field.isNullable && !field.required -> Classes.jsonNullable.asKt().parameterizedBy(type)
            !field.required || field.isNullable -> type.copy(true)
            else -> type
        }
    }

    private fun enumValueType(model: CodegenModel): TypeName {
        if (model.isString) {
            return String::class.asClassName()
        }
        if (model.isLong || "Long" == model.dataType) {
            return LONG
        }
        if (model.isInteger || "Integer" == model.dataType) {
            return INT
        }
        throw RuntimeException("Illegal enum value type")
    }


}
