package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum.*
import ru.tinkoff.kora.json.ksp.discriminatorField
import ru.tinkoff.kora.json.ksp.discriminatorValues
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCollection
import ru.tinkoff.kora.ksp.common.CommonClassNames.isMap
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName

class JsonWriterGenerator(private val resolver: Resolver) {

    fun generate(meta: JsonClassWriterMeta): TypeSpec {
        val declaration = meta.type
        val typeParameterResolver = declaration.typeParameters.toTypeParameterResolver()
        val typeName = declaration.toTypeName()
        val writerInterface = JsonTypes.jsonWriter.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(declaration.jsonWriterName())
            .generated(JsonWriterGenerator::class)
            .addOriginatingKSFile(declaration)
        typeBuilder.addSuperinterface(writerInterface)

        declaration.typeParameters.forEach {
            val typeVariableName = it.toTypeVariableName(typeParameterResolver)
            typeBuilder.addTypeVariable(TypeVariableName.invoke(typeVariableName.name, typeVariableName.bounds, null))
        }

        this.addWriters(typeBuilder, meta, typeParameterResolver)
        for (field in meta.fields) {
            typeBuilder.addProperty(
                PropertySpec.builder(this.jsonNameStaticName(field), JsonTypes.serializedString, KModifier.PRIVATE)
                    .initializer(CodeBlock.of("%T(%S)", JsonTypes.serializedString, field.jsonName))
                    .build()
            )
        }
        val functionBody = CodeBlock.builder()
        functionBody.controlFlow("if (_object == null)") {
            addStatement("_gen.writeNull()")
            addStatement("return")
        }
        functionBody.addStatement("_gen.writeStartObject(_object)")

        val discriminatorField = declaration.discriminatorField()
        if (discriminatorField != null) {
            if (meta.fields.none { it.jsonName == discriminatorField }) {
                val discriminatorValue = meta.type.discriminatorValues().first()
                functionBody.addStatement("_gen.writeFieldName(%S)", discriminatorField)
                functionBody.addStatement("_gen.writeString(%S)", discriminatorValue)
            }
        }
        for (field in meta.fields) {
            this.addWriteParam(functionBody, field)
        }
        functionBody.addStatement("_gen.writeEndObject()")

        typeBuilder.addFunction(
            FunSpec.builder("write")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameter("_gen", JsonTypes.jsonGenerator)
                .addParameter("_object", typeName.copy(nullable = true))
                .addCode(functionBody.build())
                .build()
        )

        return typeBuilder.build()
    }

    private fun addWriters(typeBuilder: TypeSpec.Builder, classMeta: JsonClassWriterMeta, typeParameterResolver: TypeParameterResolver) {
        val constructor = FunSpec.constructorBuilder()
        for (field in classMeta.fields) {
            if (field.writer != null) {
                val fieldName: String = this.writerFieldName(field)
                val fieldType = field.writer.toTypeName(typeParameterResolver)
                val writerProp = PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE)
                val writerDeclaration = field.writer.declaration as KSClassDeclaration
                if (!writerDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.OPEN)) {
                    val constructors = writerDeclaration.getConstructors().toList()
                    if (constructors.size == 1) {
                        writerProp.initializer("%T()", field.writer.toTypeName(typeParameterResolver))
                        typeBuilder.addProperty(writerProp.build())
                        continue
                    }
                }
                typeBuilder.addProperty(writerProp.build())
                constructor.addParameter(fieldName, fieldType)
                constructor.addStatement("this.%L = %L", fieldName, fieldName)
            } else if (field.typeMeta is WriterFieldType.UnknownWriterFieldType) {
                val fieldName: String = this.writerFieldName(field)
                val fieldType = JsonTypes.jsonWriter.parameterizedBy(
                    field.typeMeta.type.toTypeName(typeParameterResolver).copy(nullable = false)
                )
                val writerField = PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE)
                typeBuilder.addProperty(writerField.build())
                constructor.addParameter(fieldName, fieldType)
                constructor.addStatement("this.%L = %L", fieldName, fieldName)
            }
        }
        typeBuilder.primaryConstructor(constructor.build())
    }

    private fun writerFieldName(field: JsonClassWriterMeta.FieldMeta): String {
        return field.accessor + "Writer"
    }

    private fun addWriteParam(function: CodeBlock.Builder, field: JsonClassWriterMeta.FieldMeta) {
        val read = CodeBlock.builder()

        if (field.typeMeta.isJsonNullable) {
            read.beginControlFlow("if (it.isDefined())")
            read.addStatement("_gen.writeFieldName(%N)\n", jsonNameStaticName(field))
        } else {
            read.add("_gen.writeFieldName(%N)\n", jsonNameStaticName(field))
        }

        if (field.writer == null && field.typeMeta is WriterFieldType.KnownWriterFieldType) {
            if (field.typeMeta.isJsonNullable) {
                read.beginControlFlow("if (it.isNull)");
                read.addStatement("_gen.writeNull()")
                read.nextControlFlow("else")
                read.add(writeKnownType(field.typeMeta.knownType, field.typeMeta))
                read.endControlFlow()
            } else {
                read.add(writeKnownType(field.typeMeta.knownType, field.typeMeta))
            }
        } else {
            if (field.typeMeta.isJsonNullable) {
                read.beginControlFlow("if (it.isNull)");
                read.addStatement("_gen.writeNull()")
                read.nextControlFlow("else")
                read.addStatement("%L.write(_gen, it.value())", writerFieldName(field))
                read.endControlFlow()
            } else {
                read.addStatement("%L.write(_gen, it)", writerFieldName(field))
            }
        }

        if (field.typeMeta.isJsonNullable) {
            read.endControlFlow()
        }

        if (field.includeType == JsonClassWriterMeta.IncludeType.NON_EMPTY && (field.typeMeta.type.isCollection() || field.typeMeta.type.isMap())) {
            val letAccessor = if (field.type.isMarkedNullable) "?.let" else ".let"
            function.controlFlow("_object.%N%L {", field.accessor, letAccessor) {
                if (field.typeMeta.isJsonNullable) {
                    controlFlow("if (!it.isNull && it.value().%M())", CommonClassNames.isNotEmpty) {
                        add(read.build())
                    }
                } else {
                    controlFlow("if (it.%M())", CommonClassNames.isNotEmpty) {
                        add(read.build())
                    }
                }
            }
        } else if (field.includeType != JsonClassWriterMeta.IncludeType.ALWAYS) {
            val letAccessor = if (field.type.isMarkedNullable) "?.let" else ".let"
            function.controlFlow("_object.%N%L {", field.accessor, letAccessor) {
                add(read.build())
            }
        } else if (field.typeMeta.isJsonNullable) {
            val letAccessor = if (field.type.isMarkedNullable) "?.let" else ".let"
            function.controlFlow("_object.%N%L {", field.accessor, letAccessor) {
                add(read.build())
            }
        } else {
            function.add("_gen.writeFieldName(%L)\n", jsonNameStaticName(field))
            function.controlFlow("_object.%N.let {", field.accessor) {
                if (field.writer == null && field.typeMeta is WriterFieldType.KnownWriterFieldType) {
                    controlFlow("if (it == null)") {
                        add("_gen.writeNull()")
                        nextControlFlow("else")
                        add(writeKnownType(field.typeMeta.knownType, field.typeMeta))
                    }
                } else {
                    addStatement("%L.write(_gen, it)", writerFieldName(field))
                }
            }
        }
    }

    private fun jsonNameStaticName(field: JsonClassWriterMeta.FieldMeta): String {
        return "_" + field.fieldSimpleName.asString() + "_optimized_field_name"
    }

    private fun writeKnownType(knownType: KnownTypesEnum, fieldMeta: WriterFieldType): CodeBlock {
        val param = if (fieldMeta.isJsonNullable) "it.value()" else "it"
        return when (knownType) {
            KnownTypesEnum.STRING -> CodeBlock.of("_gen.writeString($param)\n")
            KnownTypesEnum.BOOLEAN -> CodeBlock.of("_gen.writeBoolean($param)\n")
            INTEGER, BIG_INTEGER, KnownTypesEnum.DOUBLE, KnownTypesEnum.FLOAT, KnownTypesEnum.LONG, KnownTypesEnum.SHORT -> CodeBlock.of(
                "_gen.writeNumber($param)\n"
            )

            BINARY -> CodeBlock.of("_gen.writeBinary($param)\n")
            UUID -> CodeBlock.of("_gen.writeString($param.toString())\n")
        }
    }
}
