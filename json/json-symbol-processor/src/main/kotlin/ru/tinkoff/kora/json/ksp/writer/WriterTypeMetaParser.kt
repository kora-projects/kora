package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.KnownType
import ru.tinkoff.kora.json.ksp.findJsonField
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.isJavaRecord
import ru.tinkoff.kora.ksp.common.parseAnnotationValue

class WriterTypeMetaParser(resolver: Resolver) {
    private val knownTypes: KnownType = KnownType(resolver)

    fun parse(jsonCLassDeclaration: KSClassDeclaration): JsonClassWriterMeta {
        val fieldElements = parseFields(jsonCLassDeclaration)
        val fieldMetas = mutableListOf<JsonClassWriterMeta.FieldMeta>()
        for (fieldElement in fieldElements) {
            val fieldMeta = parseField(jsonCLassDeclaration, fieldElement)
            fieldMetas.add(fieldMeta)
        }
        return JsonClassWriterMeta(jsonCLassDeclaration, fieldMetas)
    }

    private fun parseFields(jsonCLassDeclaration: KSClassDeclaration): List<KSDeclaration> {
        return if (jsonCLassDeclaration.isJavaRecord()) {
            jsonCLassDeclaration.getAllFunctions()
                .filter { f -> f.simpleName.asString() !in listOf("hashCode", "equals", "toString", "<init>") }
                .filter { p -> !p.isAnnotationPresent(JsonTypes.jsonSkipAnnotation) }
                .toList()
        } else {
            jsonCLassDeclaration.getAllProperties()
                .filter { p -> !p.isAnnotationPresent(JsonTypes.jsonSkipAnnotation) }
                .toList()
        }
    }

    private fun parseField(jsonClass: KSClassDeclaration, field: KSDeclaration): JsonClassWriterMeta.FieldMeta {
        val jsonField = findJsonField(field)
        val type = if (field is KSFunctionDeclaration) {
            field.returnType!!
        } else {
            (field as KSPropertyDeclaration).type
        }
        val resolvedType = type.resolve()
        val fieldNameConverter = jsonClass.getNameConverter()
        if (resolvedType.isError) {
            throw ProcessingErrorException("Field %s.%s is ERROR".format(jsonClass, field.simpleName.asString()), field)
        }
        val jsonName = parseJsonName(field, jsonField, fieldNameConverter)
        val accessor = field.simpleName.asString()
        val writer = jsonField?.findValueNoDefault<KSType>("writer")
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
