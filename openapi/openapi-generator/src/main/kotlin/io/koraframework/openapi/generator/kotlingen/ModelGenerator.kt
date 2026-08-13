package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenModel
import org.openapitools.codegen.CodegenProperty
import org.openapitools.codegen.model.ModelsMap
import java.nio.file.Path


class ModelGenerator : AbstractKotlinGenerator<ModelsMap>() {
    override fun generate(ctx: ModelsMap): FileSpec {
        val model = ctx.models.single().model
        val type = when {
            model.isEnum -> buildEnum(ctx, model)
            model.discriminator != null -> buildSealed(ctx, model)
            else -> buildRecord(ctx, model)
        }
        writeEnumMapperModules(ctx)
        return FileSpec.get(modelPackage, type)
    }

    private fun buildRecord(ctx: ModelsMap, model: CodegenModel): TypeSpec {
        val b = TypeSpec.classBuilder(model.classname)
        if (model.allVars.isNullOrEmpty()) {
            return b.build()
        }
        b.addModifiers(KModifier.DATA)
        buildAdditionalModelTypeAnnotations().forEach { b.addAnnotation(it) }
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
            throw IllegalArgumentException(multipleDiscriminatorFieldsError(model, discriminatorFields))
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
                enumModel.isBoolean = enumSource.isBoolean
                enumModel.isFloat = enumSource.isFloat
                enumModel.isDouble = enumSource.isDouble
                enumModel.isDecimal = enumSource.isDecimal
                enumModel.isNumber = enumSource.isNumber
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
                getValidation(field)?.let { p.addAnnotation(it.toBuilder().useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD).build()) }
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
        buildAdditionalModelTypeAnnotations().forEach { b.addAnnotation(it) }
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
            ClassName(modelPackage, contextModel.classname, model.name)
        val b = TypeSpec.enumBuilder(enumClassName)
            .addAnnotation(generated())
        buildAdditionalEnumTypeAnnotations().forEach { b.addAnnotation(it) }
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
        b.addType(
            TypeSpec.companionObjectBuilder()
                .addProperty(PropertySpec.builder("values", ARRAY.parameterizedBy(enumClassName), KModifier.PRIVATE).initializer("entries.toTypedArray()").build())
                .addFunction(
                    FunSpec.builder("fromValue")
                        .addAnnotation(JvmStatic::class)
                        .addParameter("value", enumValueType(model))
                        .returns(enumClassName)
                        .addStatement("return values.firstOrNull { it.value == value } ?: throw %T(%S + value + %S)", IllegalArgumentException::class, "Unexpected value '", "'")
                        .build()
                )
                .build()
        )
        val constants = TypeSpec.objectBuilder("Constants")
            .addAnnotation(generated())
        for (enumVar in enumVars) {
            val enumName = enumVar["name"].toString()
            constants.addProperty(PropertySpec.builder(enumName, enumValueType(model), KModifier.CONST).initializer("%L", enumVar["value"]).build())
        }
        b.addType(constants.build())

        return b.build()
    }

    private fun writeEnumMapperModules(ctx: ModelsMap) {
        val model = ctx.models.first().model
        if (model.isEnum) {
            val enumClassName = ClassName(modelPackage, model.name)
            buildEnumMapperModuleFile(enumMapperModuleName(enumClassName), listOf(enumClassName to model)).writeTo(Path.of(outputFolder))
            return
        }
        if (model.discriminator != null) {
            return
        }
        val nestedEnums = model.allVars.filter { it.isInnerEnum }.map { field ->
            val enumModel = enumModel(field)
            ClassName(modelPackage, model.classname, enumModel.name) to enumModel
        }
        if (nestedEnums.isEmpty()) {
            return
        }
        val moduleName = nestedEnumMapperModuleName(model.classname)
        buildEnumMapperModuleFile(moduleName, nestedEnums).writeTo(Path.of(outputFolder))
    }

    private fun enumModel(field: CodegenProperty): CodegenModel {
        val source = if (field.isContainer) field.items else field
        return CodegenModel().also {
            it.name = source.enumName
            it.allowableValues = source.allowableValues
            it.dataType = source.dataType
            it.description = source.description
            it.vendorExtensions = source.vendorExtensions
            it.isString = source.isString
            it.isLong = source.isLong
            it.isInteger = source.isInteger
            it.isBoolean = source.isBoolean
            it.isFloat = source.isFloat
            it.isDouble = source.isDouble
            it.isDecimal = source.isDecimal
            it.isNumber = source.isNumber
        }
    }

    private fun enumMapperModuleName(enumClassName: ClassName): String = enumClassName.simpleNames.joinToString("") + "MapperModule"

    private fun nestedEnumMapperModuleName(ownerName: String): String {
        val baseName = ownerName + "__NestedEnumMapperModule"
        val allModels = models.values.flatMap { value -> value.models.map { it.model } }
        val reserved = buildSet {
            allModels.mapTo(this) { it.classname }
            allModels.filter { it.isEnum }.mapTo(this) { enumMapperModuleName(ClassName(modelPackage, it.name)) }
        }
        var candidate = baseName
        var suffix = 2
        while (candidate in reserved) {
            candidate = baseName + suffix++
        }
        return candidate
    }

    private fun buildEnumMapperModuleFile(moduleName: String, enums: List<Pair<ClassName, CodegenModel>>): FileSpec {
        val module = TypeSpec.interfaceBuilder(moduleName)
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())
        for ((enumClassName, model) in enums) {
            addEnumMapperFactories(module, enumClassName, model)
        }
        return FileSpec.get(modelPackage, module.build())
    }

    private fun addEnumMapperFactories(module: TypeSpec.Builder, enumClassName: ClassName, model: CodegenModel) {
        val valueType = enumValueType(model)
        val methodPrefix = enumClassName.simpleName.replaceFirstChar { it.lowercase() }
        module.addFunction(
            FunSpec.builder(methodPrefix + "JsonWriter")
                .addAnnotation(Classes.defaultComponent.asKt())
                .addParameter("delegate", Classes.jsonWriter.asKt().parameterizedBy(valueType))
                .returns(Classes.jsonWriter.asKt().parameterizedBy(enumClassName))
                .addStatement("return %T(%T.entries.toTypedArray(), %T::value, delegate)", Classes.enumJsonWriter.asKt(), enumClassName, enumClassName)
                .build()
        )
        module.addFunction(
            FunSpec.builder(methodPrefix + "JsonReader")
                .addAnnotation(Classes.defaultComponent.asKt())
                .addParameter("delegate", Classes.jsonReader.asKt().parameterizedBy(valueType))
                .returns(Classes.jsonReader.asKt().parameterizedBy(enumClassName))
                .addStatement("return %T(%T.entries.toTypedArray(), %T::value, delegate)", Classes.enumJsonReader.asKt(), enumClassName, enumClassName)
                .build()
        )
        if (params.codegenMode.isClient) {
            module.addFunction(
                FunSpec.builder(methodPrefix + "StringParameterConverter")
                    .addAnnotation(Classes.defaultComponent.asKt())
                    .returns(Classes.stringParameterConverter.asKt().parameterizedBy(enumClassName))
                    .addStatement("return %T(%T.entries.toTypedArray()) { it.value.toString() }", Classes.enumStringParameterConverter.asKt(), enumClassName)
                    .build()
            )
        } else {
            module.addFunction(
                FunSpec.builder(methodPrefix + "StringParameterReader")
                    .addAnnotation(Classes.defaultComponent.asKt())
                    .returns(Classes.stringParameterReader.asKt().parameterizedBy(enumClassName))
                    .addStatement("return %T(%T.entries.toTypedArray()) { it.value.toString() }", Classes.enumStringParameterReader.asKt(), enumClassName)
                    .build()
            )
        }
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
        if ("BigDecimal" == model.dataType) {
            return java.math.BigDecimal::class.asClassName()
        }
        if (model.isDouble || "Double" == model.dataType) {
            return DOUBLE
        }
        if (model.isFloat || "Float" == model.dataType) {
            return FLOAT
        }
        if (model.isBoolean || "Boolean" == model.dataType) {
            return BOOLEAN
        }
        if (model.dataType != null && model.dataType.isNotBlank()) {
            return ClassName.bestGuess(model.dataType)
        }
        return Any::class.asTypeName()
    }

    private fun multipleDiscriminatorFieldsError(model: CodegenModel, discriminatorFields: Set<String>): String {
        return """
            Invalid OpenAPI discriminator mapping for model `${model.classname}`.

            Kora supports one discriminator field per model hierarchy, but this model inherits multiple discriminator fields: $discriminatorFields.

            Fix: use a single discriminator property across the composed schema hierarchy.
        """.trimIndent()
    }

}
