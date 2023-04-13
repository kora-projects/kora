package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.KnownType
import ru.tinkoff.kora.json.ksp.findJsonField
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.parseAnnotationValue

class ReaderTypeMetaParser(
    private val knownType: KnownType,
    private val logger: KSPLogger
) {

    fun parse(declaration: KSClassDeclaration): JsonClassReaderMeta {
        val jsonConstructor = this.findJsonConstructor(declaration)
            ?: throw ProcessingErrorException(
                "Class: %s\nTo generate json reader class must have one public constructor or constructor annotated with any of @Json/@JsonReader".format(declaration.toClassName()),
                declaration
            )
        val fields = mutableListOf<JsonClassReaderMeta.FieldMeta>()

        val nameConverter = declaration.getNameConverter()
        for (parameter in jsonConstructor.parameters) {
            val fieldMeta = parseField(declaration, parameter, nameConverter)
            fields.add(fieldMeta)
        }

        return JsonClassReaderMeta(declaration, fields)
    }

    private fun findJsonConstructor(classDeclaration: KSClassDeclaration): KSFunctionDeclaration? {
        val constructors = classDeclaration.getAllFunctions()
            .filter { it.isConstructor() }
            .filter { it.isPublic() }
            .toList()

        if (constructors.isEmpty()) {
            logger.error("No public constructor found: $classDeclaration", classDeclaration)
            return null
        }
        if (constructors.size == 1) {
            return constructors[0]
        }
        val jsonConstructors = constructors
            .filter { it.findAnnotation(JsonTypes.jsonReaderAnnotation) != null || it.findAnnotation(JsonTypes.json) != null }
            .toList()
        if (jsonConstructors.size == 1) {
            return jsonConstructors[0]
        }
        val nonEmpty = constructors
            .filter { it.parameters.isNotEmpty() }
            .toList()
        if (nonEmpty.size == 1) {
            return nonEmpty[0]
        }
        return null
    }

    private fun parseField(jsonClass: KSClassDeclaration, parameter: KSValueParameter, nameConverter: NameConverter?): JsonClassReaderMeta.FieldMeta {
        val jsonField = findJsonField(parameter, jsonClass)
        val jsonName = parseJsonName(parameter, jsonField, nameConverter)
        val fieldType = parameter.type.resolve()
        val typeName = parameter.type.toTypeName(jsonClass.typeParameters.toTypeParameterResolver())
        val reader = jsonField?.findValueNoDefault<KSType>("reader")
        val typeMeta = this.parseReaderFieldType(fieldType, typeName)
        return JsonClassReaderMeta.FieldMeta(parameter, jsonName, typeName, typeMeta, reader)
    }

    private fun parseReaderFieldType(resolvedFieldType: KSType, resolvedFieldTypeName: TypeName): ReaderFieldType {
        val knownType = knownType.detect(resolvedFieldType)
        return if (knownType != null) {
            ReaderFieldType.KnownTypeReaderMeta(knownType, resolvedFieldTypeName)
        } else {
            ReaderFieldType.UnknownTypeReaderMeta(resolvedFieldTypeName)
        }
    }


    private fun parseJsonName(param: KSValueParameter, jsonField: KSAnnotation?, nameConverter: NameConverter?): String {
        if (jsonField == null) {
            return if (nameConverter != null) {
                nameConverter.convert(param.name!!.asString())
            } else {
                param.name!!.asString()
            }
        }
        val jsonFieldValue = parseAnnotationValue<String>(jsonField, "value")
        if (jsonFieldValue != null && jsonFieldValue.isNotBlank()) {
            return jsonFieldValue
        } else {
            return param.name!!.asString()
        }
    }

}
