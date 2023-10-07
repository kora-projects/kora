package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.KnownType
import ru.tinkoff.kora.json.ksp.findJsonField
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.JavaUtils.recordComponents
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.isJavaRecord
import ru.tinkoff.kora.ksp.common.parseAnnotationValue

class WriterTypeMetaParser(val resolver: Resolver) {
    private val knownTypes: KnownType = KnownType(resolver)

    fun parse(jsonClassDeclaration: KSClassDeclaration): JsonClassWriterMeta {
        val fieldElements = parseFields(jsonClassDeclaration)
        val fieldMetas = mutableListOf<JsonClassWriterMeta.FieldMeta>()
        for (fieldElement in fieldElements) {
            val jsonField = when (fieldElement) {
                is KSFunctionDeclaration -> fieldElement.findJsonField()
                is KSPropertyDeclaration -> fieldElement.findJsonField()
                is KSValueParameter -> fieldElement.findJsonField()
                else -> throw IllegalStateException()
            }
            val fieldMeta = parseField(jsonClassDeclaration, fieldElement, jsonField)
            fieldMetas.add(fieldMeta)
        }
        return JsonClassWriterMeta(jsonClassDeclaration, fieldMetas)
    }

    private fun parseFields(jsonClassDeclaration: KSClassDeclaration): List<KSDeclaration> {
        return if (jsonClassDeclaration.isJavaRecord()) {
            jsonClassDeclaration.recordComponents()
                .filter { p -> !p.isAnnotationPresent(JsonTypes.jsonSkipAnnotation) }
                .toList()
        } else {
            jsonClassDeclaration.getAllProperties()
                .filter { p -> !p.isAnnotationPresent(JsonTypes.jsonSkipAnnotation) }
                .toList()
        }
    }

    private fun parseField(jsonClassDeclaration: KSClassDeclaration, field: KSDeclaration, jsonField: KSAnnotation?): JsonClassWriterMeta.FieldMeta {
        val type = if (field is KSFunctionDeclaration) {
            field.returnType
        } else {
            (field as KSPropertyDeclaration).type
        }
        val resolvedType = type!!.resolve()
        val fieldNameConverter = jsonClassDeclaration.getNameConverter()
        if (resolvedType.isError) {
            throw ProcessingErrorException("Field %s.%s is ERROR".format(jsonClassDeclaration, field.simpleName.asString()), field)
        }
        val jsonName = parseJsonName(field, jsonField, fieldNameConverter)
        val accessor = field.simpleName.asString()
        val writer = jsonField?.findValueNoDefault<KSType>("writer")
        val typeMeta = parseWriterFieldType(type, resolvedType)

        val includeType = ((field.findAnnotation(JsonTypes.jsonInclude)
            ?: jsonClassDeclaration.findAnnotation(JsonTypes.jsonInclude))
            ?.arguments?.filter { a -> (a.name?.getShortName() ?: "") == "value" }
            ?.firstNotNullOfOrNull { arg -> JsonClassWriterMeta.IncludeType.tryParse(ClassName.bestGuess(arg.value.toString()).simpleName) }
            ?: JsonClassWriterMeta.IncludeType.NON_NULL)

        return JsonClassWriterMeta.FieldMeta(field.simpleName, jsonName, type.resolve(), typeMeta, writer, accessor, includeType)
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
