package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum.*
import ru.tinkoff.kora.json.ksp.discriminatorField
import ru.tinkoff.kora.json.ksp.discriminatorValues
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName

class JsonWriterGenerator(
    private val resolver: Resolver
) {

    fun generate(meta: JsonClassWriterMeta): TypeSpec {
        val jsonClassDeclaration = meta.classDeclaration
        val typeParameterResolver = jsonClassDeclaration.typeParameters.toTypeParameterResolver()
        val typeName = meta.classDeclaration.toTypeName()
        val writerInterface = JsonTypes.jsonWriter.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(meta.classDeclaration.jsonWriterName())
            .generated(JsonWriterGenerator::class)
        jsonClassDeclaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }
        typeBuilder.addSuperinterface(writerInterface)

        meta.classDeclaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName())
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

        val discriminatorField = meta.classDeclaration.discriminatorField()
        if (discriminatorField != null) {
            if (meta.fields.none { it.jsonName == discriminatorField }) {
                val discriminatorValue = meta.classDeclaration.discriminatorValues().first()
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
        function.controlFlow("_object.%N%L.let {", field.accessor, if (field.type.isMarkedNullable) "?" else "") {
            function.add("_gen.writeFieldName(%L)\n", jsonNameStaticName(field))
            if (field.writer == null && field.typeMeta is WriterFieldType.KnownWriterFieldType) {
                function.add(writeKnownType(field.typeMeta.knownType))
            } else {
                function.addStatement("%L.write(_gen, it)", writerFieldName(field))
            }
        }
    }

    private fun jsonNameStaticName(field: JsonClassWriterMeta.FieldMeta): String {
        return "_" + field.fieldSimpleName.asString() + "_optimized_field_name"
    }

    private fun writeKnownType(knownType: KnownTypesEnum) = when (knownType) {
        KnownTypesEnum.STRING -> CodeBlock.of("_gen.writeString(it)\n")
        KnownTypesEnum.BOOLEAN -> CodeBlock.of("_gen.writeBoolean(it)\n")
        INTEGER, BIG_INTEGER, BIG_DECIMAL, KnownTypesEnum.DOUBLE, KnownTypesEnum.FLOAT, KnownTypesEnum.LONG, KnownTypesEnum.SHORT -> CodeBlock.of(
            "_gen.writeNumber(it)\n"
        )

        BINARY -> CodeBlock.of("_gen.writeBinary(it)\n")
        UUID -> CodeBlock.of("_gen.writeString(it.toString())\n")
    }
}
