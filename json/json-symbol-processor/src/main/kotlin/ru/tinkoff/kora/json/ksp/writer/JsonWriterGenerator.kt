package ru.tinkoff.kora.json.ksp.writer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.io.SerializedString
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.json.common.EnumJsonWriter
import ru.tinkoff.kora.json.common.JsonObjectCodec
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorValue
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum.*
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import javax.annotation.processing.Generated

@KspExperimental
class JsonWriterGenerator(
    private val resolver: Resolver
) {
    private val writerErasure = resolver.getClassDeclarationByName(JsonWriter::class.qualifiedName!!)!!.asStarProjectedType()
    private val enumType = resolver.getClassDeclarationByName<Enum<*>>()!!.asStarProjectedType().makeNotNullable()

    fun generate(meta: JsonClassWriterMeta): TypeSpec {
        val jsonClassDeclaration = meta.type.declaration as KSClassDeclaration
        val typeParameterResolver = jsonClassDeclaration.typeParameters.toTypeParameterResolver()
        val writedType = if (meta.type.declaration.typeParameters.isEmpty()) {
            meta.type.toTypeName(typeParameterResolver)
        } else {
            meta.type.toClassName().parameterizedBy(meta.type.declaration.typeParameters.map { it.toTypeVariableName(typeParameterResolver) })
        }
        val writerInterface = writerErasure.toClassName().parameterizedBy(writedType)
        val typeBuilder = TypeSpec.classBuilder(jsonWriterName(meta.type))
            .addAnnotation(
                AnnotationSpec.builder(Generated::class)
                    .addMember(CodeBlock.of("%S", JsonWriterGenerator::class.qualifiedName!!))
                    .build()
            )
        jsonClassDeclaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }

        if (enumType.isAssignableFrom(meta.type)) {
            return this.generateForEnum(meta, typeBuilder, typeParameterResolver)
        }
        typeBuilder.addSuperinterface(writerInterface)

        meta.type.declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName())
        }

        this.addWriters(typeBuilder, meta, typeParameterResolver)
        for (field in meta.fields) {
            typeBuilder.addProperty(
                PropertySpec.builder(this.jsonNameStaticName(field), SerializedString::class, KModifier.PRIVATE)
                    .initializer(CodeBlock.of("%T(%S)", SerializedString::class.java, field.jsonName))
                    .build()
            )
        }
        val functionBody = CodeBlock.builder()
        functionBody.controlFlow("if (_object == null)") {
            addStatement("_gen.writeNull()")
            addStatement("return")
        }
        functionBody.addStatement("_gen.writeStartObject(_object)")
        if (meta.discriminatorField != null) {
            val discriminatorValueAnnotation = jsonClassDeclaration.getAnnotationsByType(JsonDiscriminatorValue::class).firstOrNull()
            val discriminatorValue = discriminatorValueAnnotation?.value ?: jsonClassDeclaration.simpleName.asString()
            functionBody.addStatement("_gen.writeFieldName(%S)", meta.discriminatorField)
            functionBody.addStatement("_gen.writeString(%S)", discriminatorValue)
        }
        for (field in meta.fields) {
            this.addWriteParam(functionBody, field, typeParameterResolver)
        }
        functionBody.addStatement("_gen.writeEndObject()")

        typeBuilder.addFunction(
            FunSpec.builder("write")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameter("_gen", JsonGenerator::class)
                .addParameter("_object", writedType.copy(true))
                .addCode(functionBody.build())
                .build()
        )

        return typeBuilder.build()
    }

    private fun generateForEnum(meta: JsonClassWriterMeta, typeBuilder: TypeSpec.Builder, typeParameterResolver: TypeParameterResolver): TypeSpec {
        val writerInterface = writerErasure.toClassName().parameterizedBy(meta.type.makeNotNullable().toTypeName(typeParameterResolver))
        typeBuilder.addSuperinterface(writerInterface, CodeBlock.of("%T(%T.values(), { it.toString() })", EnumJsonWriter::class, meta.type.toClassName()))
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
                val fieldType = JsonWriter::class.asClassName().parameterizedBy(
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

    private fun addWriteParam(function: CodeBlock.Builder, field: JsonClassWriterMeta.FieldMeta, typeParameterResolver: TypeParameterResolver) {
        function.add("_gen.writeFieldName(%L)\n", this.jsonNameStaticName(field))
        if (field.writer == null && field.typeMeta is WriterFieldType.KnownWriterFieldType) {
            function.add(this.writeKnownType(field.typeMeta.knownType, field.typeMeta.markedNullable, CodeBlock.of("_object.%L", field.accessor)))
        } else if (field.writer == null) {
            function.controlFlow("if (_object.%L == null)", field.accessor) {
                addStatement("_gen.writeNull()")
                nextControlFlow("else")
                addStatement("%L.write(_gen, _object.%L!!)", writerFieldName(field), field.accessor)
            }
        } else {
            function.addStatement("%L.write(_gen, _object.%L)", this.writerFieldName(field), field.accessor)
        }
    }

    private fun jsonNameStaticName(field: JsonClassWriterMeta.FieldMeta): String {
        return "_" + field.fieldSimpleName.asString() + "_optimized_field_name"
    }

    private fun writeKnownType(
        knownType: KnownTypesEnum,
        isMarkedNullable: Boolean,
        value: CodeBlock
    ): CodeBlock {
        val nullableCodeBlock = if (isMarkedNullable) CodeBlock.of("if (%L == null) _gen.writeNull() else ", value) else CodeBlock.of("")

        return when (knownType) {
            KnownTypesEnum.STRING -> CodeBlock.of("%L_gen.writeString(%L)\n", nullableCodeBlock, value)
            KnownTypesEnum.BOOLEAN -> CodeBlock.of("%L_gen.writeBoolean(%L)\n", nullableCodeBlock, value)
            INTEGER, BIG_INTEGER, BIG_DECIMAL, KnownTypesEnum.DOUBLE, KnownTypesEnum.FLOAT, KnownTypesEnum.LONG, KnownTypesEnum.SHORT -> CodeBlock.of(
                "%L_gen.writeNumber(%L)\n",
                nullableCodeBlock,
                value
            )

            BINARY -> CodeBlock.of("%L_gen.writeBinary(%L)\n", nullableCodeBlock, value)
            LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME -> CodeBlock.of(
                "%L_gen.writeString(%L.toString())\n",
                nullableCodeBlock,
                value
            )
            UUID -> CodeBlock.of("%L_gen.writeString(%L.toString())\n", nullableCodeBlock, value)
        }
    }
}
