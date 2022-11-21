package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.json.common.annotation.JsonField
import ru.tinkoff.kora.json.common.annotation.JsonField.DefaultWriter
import ru.tinkoff.kora.json.common.annotation.JsonSkip
import ru.tinkoff.kora.json.ksp.KnownType
import ru.tinkoff.kora.json.ksp.discriminator
import ru.tinkoff.kora.json.ksp.isSealed
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.isJavaRecord
import ru.tinkoff.kora.ksp.common.parseAnnotationValue

@KspExperimental
class WriterTypeMetaParser(
    resolver: Resolver,
    private val log: KSPLogger
) {
    private val knownTypes: KnownType = KnownType(resolver)
    private val jsonFieldDeclaration = resolver.getClassDeclarationByName(JsonField::class.qualifiedName!!)!!
    private val jsonFieldType = jsonFieldDeclaration.asStarProjectedType()
    private val defaultWriter = resolver.getClassDeclarationByName(DefaultWriter::class.qualifiedName!!)!!.asStarProjectedType()

    fun parse(typeRef: KSTypeReference, discriminatorRequired: Boolean = false): JsonClassWriterMeta? {
        val jsonType = typeRef.resolve()
        val jsonCLassDeclaration = when (jsonType.declaration) {
            is KSTypeAlias -> (jsonType.declaration as KSTypeAlias).type.resolve().declaration as KSClassDeclaration
            is KSClassDeclaration -> jsonType.declaration as KSClassDeclaration
            else -> return null
        }

        val discriminator = if (discriminatorRequired) {
            discriminator(jsonCLassDeclaration)
        } else {
            null
        }
        if (isSealed(jsonCLassDeclaration)) {
            return JsonClassWriterMeta(
                jsonType,
                typeRef,
                emptyList(),
                discriminator,
                true,
            )
        }


        val fieldElements = parseFields(jsonCLassDeclaration)
        val fieldMetas = mutableListOf<JsonClassWriterMeta.FieldMeta>()
        for (fieldElement in fieldElements) {
            val fieldMeta = parseField(jsonCLassDeclaration, fieldElement)
            if (fieldMeta == null) {
                return null
            } else {
                fieldMetas.add(fieldMeta)
            }
        }
        return JsonClassWriterMeta(
            jsonType,
            typeRef,
            fieldMetas,
            discriminator,
            false
        )
    }

    private fun parseFields(jsonCLassDeclaration: KSClassDeclaration): List<KSDeclaration> {
        return if (jsonCLassDeclaration.isJavaRecord()) {
            jsonCLassDeclaration.getAllFunctions()
                .filter { f -> f.simpleName.asString() !in listOf("hashCode", "equals", "toString", "<init>") }
                .filter { p -> !p.isAnnotationPresent(JsonSkip::class) }
                .toList()
        } else {
            jsonCLassDeclaration.getAllProperties()
                .filter { p -> !p.isAnnotationPresent(JsonSkip::class) }
                .toList()
        }
    }

    private fun parseField(jsonClass: KSClassDeclaration, field: KSDeclaration): JsonClassWriterMeta.FieldMeta? {
        val jsonField = findJsonField(field)
        val type = if (field is KSFunctionDeclaration) {
            field.returnType!!
        } else {
            (field as KSPropertyDeclaration).type
        }
        val resolvedType = type.resolve()
        val fieldNameConverter = jsonClass.getNameConverter()
        if (resolvedType.isError) {
            log.error("Field %s.%s is ERROR".format(jsonClass, field.simpleName.asString()), field)
            return null
        }
        val jsonName = parseJsonName(field, jsonField, fieldNameConverter)
        val accessor = field.simpleName.asString()
        val writerFieldValue = jsonField?.let { parseAnnotationValue<KSType>(jsonField, "writer") }
        val writer = if (writerFieldValue == null) null else if (defaultWriter == writerFieldValue) null else writerFieldValue
        val typeMeta = parseWriterFieldType(type, resolvedType)
        return JsonClassWriterMeta.FieldMeta(field.simpleName, jsonName, type.resolve(), typeMeta, writer, accessor)
    }

    private fun parseWriterFieldType(type: KSTypeReference, resolvedType: KSType): WriterFieldType {
        val realType = if (resolvedType.nullability == Nullability.PLATFORM) {
            resolvedType.makeNullable()
        } else {
            resolvedType
        }
        val knownType = knownTypes.detect(realType)
        return if (knownType != null) {
            WriterFieldType.KnownWriterFieldType(knownType, realType.isMarkedNullable)
        } else {
            WriterFieldType.UnknownWriterFieldType(type)
        }
    }

    private fun findJsonField(param: KSDeclaration): KSAnnotation? {
        return param.annotations.firstOrNull { it.annotationType.resolve() == jsonFieldType }
    }

    private fun parseJsonName(param: KSDeclaration, jsonField: KSAnnotation?, nameConverter: NameConverter?): String {
        if (jsonField == null) {
            return if (nameConverter != null) {
                nameConverter.convert(param.simpleName.asString())
            } else {
                param.simpleName.asString()
            }
        }
        val jsonFieldValue = parseAnnotationValue<String>(jsonField, "value")
        return if (jsonFieldValue != null && jsonFieldValue.isNotBlank()) {
            jsonFieldValue
        } else param.simpleName.asString()
    }

}
