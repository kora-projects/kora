package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.FieldFactory
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.getOuterClassesAsPrefix
import io.koraframework.ksp.common.parseMappingData

class MongoCodecGenerator(private val codeGenerator: CodeGenerator) {

    companion object {
        private const val WRITER = "_writer"
        private const val READER = "_reader"
        private const val VALUE = "_value"
        private const val CONTEXT = "_context"

        fun codecName(declaration: KSClassDeclaration): String =
            declaration.getOuterClassesAsPrefix() + declaration.simpleName.asString() + "_MongoCodec"
    }

    fun generate(entity: MongoEntity) {
        val declaration = entity.declaration
        if (declaration.typeParameters.isNotEmpty()) {
            throw ProcessingErrorException(
                """
                Mongo entity type is invalid:
                  ${declaration.qualifiedName?.asString()}

                Problem:
                  @EntityMongo does not support generic types.

                Hint:
                  A generated codec must report a concrete Class<T> from getEncoderClass(), which a generic type can not provide.

                Fix:
                  Use a non-generic entity type, or supply a custom Codec for this type.
                """.trimIndent(), declaration
            )
        }

        val entityType = declaration.toClassName()
        val codecName = codecName(declaration)
        val type = TypeSpec.classBuilder(codecName)
            .generated(MongoCodecGenerator::class)
            .addOriginatingKSFile(declaration)
            .addSuperinterface(MongoTypes.codec.parameterizedBy(entityType))
        val constructor = FunSpec.constructorBuilder()
        val codecs = FieldFactory(type, constructor, "_codec_")

        type.addFunction(this.generateEncode(entity, entityType, codecs))
        type.addFunction(this.generateDecode(entity, entityType, codecs))
        type.addFunction(
            FunSpec.builder("getEncoderClass")
                .addModifiers(KModifier.OVERRIDE)
                .returns(ClassName("java.lang", "Class").parameterizedBy(entityType))
                .addStatement("return %T::class.java", entityType)
                .build()
        )
        type.primaryConstructor(constructor.build())

        val packageName = declaration.packageName.asString()
        FileSpec.builder(packageName, codecName)
            .addType(type.build())
            .build()
            .writeTo(this.codeGenerator, aggregating = false)
    }

    private fun generateEncode(entity: MongoEntity, entityType: ClassName, codecs: FieldFactory): FunSpec {
        val encode = FunSpec.builder("encode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(WRITER, MongoTypes.bsonWriter)
            .addParameter(VALUE, entityType)
            .addParameter(CONTEXT, MongoTypes.encoderContext)

        val names = Names()
        encode.addStatement("%N.writeStartDocument()", WRITER)
        for (field in entity.fields) {
            val accessor = CodeBlock.of("%N.%N", VALUE, field.name)
            if (field.bsonName == "_id" && field.nullable) {
                val local = names.next("_v")
                encode.addStatement("val %N = %L", local, accessor)
                encode.beginControlFlow("if (%N != null)", local)
                encode.addStatement("%N.writeName(%S)", WRITER, field.bsonName)
                encode.addCode(this.writeValue(field.type, CodeBlock.of("%N", local), field.annotated, codecs, names))
                encode.endControlFlow()
                continue
            }
            encode.addStatement("%N.writeName(%S)", WRITER, field.bsonName)
            if (field.nullable) {
                val local = names.next("_v")
                encode.addStatement("val %N = %L", local, accessor)
                encode.beginControlFlow("if (%N == null)", local)
                encode.addStatement("%N.writeNull()", WRITER)
                encode.nextControlFlow("else")
                encode.addCode(this.writeValue(field.type, CodeBlock.of("%N", local), field.annotated, codecs, names))
                encode.endControlFlow()
            } else {
                encode.addCode(this.writeValue(field.type, accessor, field.annotated, codecs, names))
            }
        }
        encode.addStatement("%N.writeEndDocument()", WRITER)
        return encode.build()
    }

    private fun writeValue(type: KSType, valueExpr: CodeBlock, annotated: KSAnnotated, codecs: FieldFactory, names: Names): CodeBlock {
        val mapping = annotated.parseMappingData().getMapping(MongoTypes.codec)
        if (mapping == null) {
            val nativeType = MongoNativeTypes.find(type.declaration.qualifiedName?.asString())
            if (nativeType != null) {
                return CodeBlock.builder().addStatement("%L", nativeType.write(WRITER, valueExpr)).build()
            }
            if ((type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS) {
                return CodeBlock.builder().addStatement("%N.writeString(%L.name)", WRITER, valueExpr).build()
            }
            val elementType = collectionElementType(type)
            if (elementType != null) {
                return this.writeCollection(elementType, valueExpr, annotated, codecs, names)
            }
            val mapValueType = mapValueType(type, annotated)
            if (mapValueType != null) {
                return this.writeMap(mapValueType, valueExpr, annotated, codecs, names)
            }
        }

        val codecField = codecs.add(mapping, MongoTypes.codec.parameterizedBy(type.toTypeName().copy(false)))
        return CodeBlock.builder().addStatement("this.%N.encode(%N, %L, %N)", codecField, WRITER, valueExpr, CONTEXT).build()
    }

    private fun writeCollection(elementType: KSType, valueExpr: CodeBlock, annotated: KSAnnotated, codecs: FieldFactory, names: Names): CodeBlock {
        val element = names.next("_e")
        return CodeBlock.builder()
            .addStatement("%N.writeStartArray()", WRITER)
            .beginControlFlow("for (%N in %L)", element, valueExpr)
            .apply {
                if (elementType.isMarkedNullable) {
                    beginControlFlow("if (%N == null)", element)
                    addStatement("%N.writeNull()", WRITER)
                    nextControlFlow("else")
                    add(writeValue(elementType, CodeBlock.of("%N", element), annotated, codecs, names))
                    endControlFlow()
                } else {
                    add(writeValue(elementType, CodeBlock.of("%N", element), annotated, codecs, names))
                }
            }
            .endControlFlow()
            .addStatement("%N.writeEndArray()", WRITER)
            .build()
    }

    private fun writeMap(valueType: KSType, valueExpr: CodeBlock, annotated: KSAnnotated, codecs: FieldFactory, names: Names): CodeBlock {
        val entry = names.next("_entry")
        return CodeBlock.builder()
            .addStatement("%N.writeStartDocument()", WRITER)
            .beginControlFlow("for (%N in %L)", entry, valueExpr)
            .addStatement("%N.writeName(%N.key)", WRITER, entry)
            .apply {
                if (valueType.isMarkedNullable) {
                    beginControlFlow("if (%N.value == null)", entry)
                    addStatement("%N.writeNull()", WRITER)
                    nextControlFlow("else")
                    add(writeValue(valueType, CodeBlock.of("%N.value", entry), annotated, codecs, names))
                    endControlFlow()
                } else {
                    add(writeValue(valueType, CodeBlock.of("%N.value", entry), annotated, codecs, names))
                }
            }
            .endControlFlow()
            .addStatement("%N.writeEndDocument()", WRITER)
            .build()
    }

    private fun generateDecode(entity: MongoEntity, entityType: ClassName, codecs: FieldFactory): FunSpec {
        val decode = FunSpec.builder("decode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(READER, MongoTypes.bsonReader)
            .addParameter(CONTEXT, MongoTypes.decoderContext)
            .returns(entityType)

        val names = Names()
        for (field in entity.fields) {
            decode.addStatement("var %N: %T = null", field.variableName, field.type.toTypeName().copy(true))
        }

        decode.addStatement("%N.readStartDocument()", READER)
        decode.beginControlFlow("while (%N.readBsonType() != %T.END_OF_DOCUMENT)", READER, MongoTypes.bsonType)
        decode.beginControlFlow("when (%N.readName())", READER)
        for (field in entity.fields) {
            decode.beginControlFlow("%S ->", field.bsonName)
            decode.beginControlFlow("if (%N.currentBsonType == %T.NULL)", READER, MongoTypes.bsonType)
            decode.addStatement("%N.readNull()", READER)
            decode.nextControlFlow("else")
            decode.addCode(this.readValue(field.type, field.variableName, field.annotated, codecs, names))
            decode.endControlFlow()
            decode.endControlFlow()
        }
        decode.addStatement("else -> %N.skipValue()", READER)
        decode.endControlFlow()
        decode.endControlFlow()
        decode.addStatement("%N.readEndDocument()", READER)

        val arguments = CodeBlock.builder()
        for ((index, field) in entity.fields.withIndex()) {
            if (index > 0) {
                arguments.add(", ")
            }
            if (field.nullable) {
                arguments.add("%N", field.variableName)
            } else {
                arguments.add(
                    "%N ?: throw %T(%S)", field.variableName, NullPointerException::class,
                    "Field ${entity.declaration.simpleName.asString()}.${field.name} is not nullable, but document field '${field.bsonName}' is null or absent"
                )
            }
        }
        decode.addStatement("return %T(%L)", entityType, arguments.build())
        return decode.build()
    }

    private fun readValue(type: KSType, target: String, annotated: KSAnnotated, codecs: FieldFactory, names: Names): CodeBlock {
        val mapping = annotated.parseMappingData().getMapping(MongoTypes.codec)
        if (mapping == null) {
            val nativeType = MongoNativeTypes.find(type.declaration.qualifiedName?.asString())
            if (nativeType != null) {
                return CodeBlock.builder().addStatement("%N = %L", target, nativeType.read(READER)).build()
            }
            if ((type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS) {
                return CodeBlock.builder()
                    .addStatement("%N = %T.valueOf(%N.readString())", target, type.toTypeName().copy(false), READER)
                    .build()
            }
            val elementType = collectionElementType(type)
            if (elementType != null) {
                return this.readCollection(type, elementType, target, annotated, codecs, names)
            }
            val mapValueType = mapValueType(type, annotated)
            if (mapValueType != null) {
                return this.readMap(mapValueType, target, annotated, codecs, names)
            }
        }

        val codecField = codecs.add(mapping, MongoTypes.codec.parameterizedBy(type.toTypeName().copy(false)))
        return CodeBlock.builder().addStatement("%N = this.%N.decode(%N, %N)", target, codecField, READER, CONTEXT).build()
    }

    private fun readCollection(collectionType: KSType, elementType: KSType, target: String, annotated: KSAnnotated, codecs: FieldFactory, names: Names): CodeBlock {
        val collection = names.next("_c")
        val element = names.next("_i")
        val implementation = if (isSet(collectionType)) ClassName("kotlin.collections", "LinkedHashSet") else ClassName("kotlin.collections", "ArrayList")

        return CodeBlock.builder()
            .addStatement("val %N = %T<%T>()", collection, implementation, elementType.toTypeName())
            .addStatement("%N.readStartArray()", READER)
            .beginControlFlow("while (%N.readBsonType() != %T.END_OF_DOCUMENT)", READER, MongoTypes.bsonType)
            .beginControlFlow("if (%N.currentBsonType == %T.NULL)", READER, MongoTypes.bsonType)
            .addStatement("%N.readNull()", READER)
            .apply {
                if (elementType.isMarkedNullable) {
                    addStatement("%N.add(null)", collection)
                }
            }
            .nextControlFlow("else")
            .addStatement("var %N: %T = null", element, elementType.toTypeName().copy(true))
            .add(this.readValue(elementType, element, annotated, codecs, names))
            .addStatement("%N.add(%N!!)", collection, element)
            .endControlFlow()
            .endControlFlow()
            .addStatement("%N.readEndArray()", READER)
            .addStatement("%N = %N", target, collection)
            .build()
    }

    private fun readMap(valueType: KSType, target: String, annotated: KSAnnotated, codecs: FieldFactory, names: Names): CodeBlock {
        val map = names.next("_m")
        val key = names.next("_k")
        val value = names.next("_mv")

        return CodeBlock.builder()
            .addStatement("val %N = %T<%T, %T>()", map, ClassName("kotlin.collections", "LinkedHashMap"), String::class, valueType.toTypeName())
            .addStatement("%N.readStartDocument()", READER)
            .beginControlFlow("while (%N.readBsonType() != %T.END_OF_DOCUMENT)", READER, MongoTypes.bsonType)
            .addStatement("val %N = %N.readName()", key, READER)
            .beginControlFlow("if (%N.currentBsonType == %T.NULL)", READER, MongoTypes.bsonType)
            .addStatement("%N.readNull()", READER)
            .apply {
                if (valueType.isMarkedNullable) {
                    addStatement("%N[%N] = null", map, key)
                }
            }
            .nextControlFlow("else")
            .addStatement("var %N: %T = null", value, valueType.toTypeName().copy(true))
            .add(this.readValue(valueType, value, annotated, codecs, names))
            .addStatement("%N[%N] = %N!!", map, key, value)
            .endControlFlow()
            .endControlFlow()
            .addStatement("%N.readEndDocument()", READER)
            .addStatement("%N = %N", target, map)
            .build()
    }

    private class Names {
        private var counter = 0
        fun next(prefix: String): String = prefix + (++counter)
    }
}

internal fun collectionElementType(type: KSType): KSType? {
    val qualifiedName = type.declaration.qualifiedName?.asString() ?: return null
    if (qualifiedName != "kotlin.collections.List" && qualifiedName != "kotlin.collections.Set" && qualifiedName != "kotlin.collections.Collection") {
        return null
    }
    return type.arguments.singleOrNull()?.type?.resolve()
}

internal fun isSet(type: KSType): Boolean = type.declaration.qualifiedName?.asString() == "kotlin.collections.Set"

internal fun mapValueType(type: KSType, annotated: KSAnnotated): KSType? {
    if (type.declaration.qualifiedName?.asString() != "kotlin.collections.Map") {
        return null
    }
    if (type.arguments.size != 2) {
        return null
    }
    val keyType = type.arguments[0].type!!.resolve()
    if (keyType.declaration.qualifiedName?.asString() != "kotlin.String") {
        throw ProcessingErrorException(
            """
            Mongo entity field is invalid:
              map key type is ${keyType.declaration.qualifiedName?.asString()}

            Problem:
              A BSON document can only have string keys.

            Hint:
              A Map field is stored as a nested document whose field names are the map keys.

            Fix:
              Use Map<String, *>, or supply a Codec for this field via @Mapping.
            """.trimIndent(), annotated
        )
    }
    return type.arguments[1].type!!.resolve()
}
