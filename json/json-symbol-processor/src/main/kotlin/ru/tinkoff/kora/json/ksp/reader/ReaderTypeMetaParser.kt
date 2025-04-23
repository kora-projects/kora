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
        if (declaration.classKind != ClassKind.CLASS) {
            throw ProcessingErrorException("JsonReader can be generated only for types that are class/data class/sealed, but called for: ${declaration.qualifiedName!!.asString()}", declaration)
        }
        if (declaration.modifiers.contains(Modifier.ABSTRACT)) {
            throw ProcessingErrorException("JsonReader can't be generated for abstract types, but called for: ${declaration.qualifiedName!!.asString()}", declaration)
        }

        val jsonConstructor = this.findJsonConstructor(declaration)
        val fields = mutableListOf<JsonClassReaderMeta.FieldMeta>()

        val nameConverter = declaration.getNameConverter()
        for (parameter in jsonConstructor.parameters) {
            val jsonField = parameter.findJsonField()
            val fieldMeta = parseField(declaration, parameter, jsonField, nameConverter)
            fields.add(fieldMeta)
        }

        return JsonClassReaderMeta(declaration, fields)
    }

    private fun findJsonConstructor(classDeclaration: KSClassDeclaration): KSFunctionDeclaration {
        val constructors = classDeclaration.getAllFunctions()
            .filter { it.isConstructor() }
            .filter { it.isPublic() }
            .toList()

        if (constructors.isEmpty()) {
            throw ProcessingErrorException(
                "Class has no public constructors: %s\nTo generate json reader class must have one public constructor or constructor annotated with any of @Json/@JsonReader".format(classDeclaration.toClassName()),
                classDeclaration
            )
        } else if (constructors.size == 1) {
            return constructors[0]
        }

        val jsonReaderConstructors = constructors
            .filter { it.findAnnotation(JsonTypes.jsonReaderAnnotation) != null }
            .toList()
        if (jsonReaderConstructors.size == 1) {
            return jsonReaderConstructors[0]
        }
        if (jsonReaderConstructors.isNotEmpty()) {
            throw ProcessingErrorException(
                "Class: %s\nMultiple constructor annotated with @JsonReader".format(classDeclaration.toClassName()),
                classDeclaration
            )
        }

        val jsonConstructors = constructors
            .filter { it.findAnnotation(JsonTypes.json) != null }
            .toList()
        if (jsonConstructors.size == 1) {
            return jsonConstructors[0]
        }
        if (jsonConstructors.isNotEmpty()) {
            throw ProcessingErrorException(
                "Class: %s\nMultiple constructor annotated with @Json".format(classDeclaration.toClassName()),
                classDeclaration
            )
        }

        val nonEmpty = constructors
            .filter { it.parameters.isNotEmpty() }
            .toList()
        if (nonEmpty.size == 1) {
            return nonEmpty[0]
        }

        throw ProcessingErrorException(
            "Class: %s\nTo generate json reader class must have one public constructor or constructor annotated with any of @Json/@JsonReader".format(classDeclaration.toClassName()),
            classDeclaration
        )
    }

    private fun parseField(jsonClass: KSClassDeclaration, parameter: KSValueParameter, jsonField: KSAnnotation?, nameConverter: NameConverter?): JsonClassReaderMeta.FieldMeta {
        val jsonName = parseJsonName(parameter, jsonField, nameConverter)
        val reader = jsonField?.findValueNoDefault<KSType>("reader")
        val typeMeta = this.parseReaderFieldType(jsonClass, parameter)
        val fieldTypeName = parameter.type.toTypeName(jsonClass.typeParameters.toTypeParameterResolver())
        return JsonClassReaderMeta.FieldMeta(parameter, jsonName, fieldTypeName, typeMeta, reader)
    }

    private fun parseReaderFieldType(jsonClass: KSClassDeclaration, parameter: KSValueParameter): ReaderFieldType {
        var isJsonNullable = false
        var realType = parameter.type.resolve()
        val resolvedFieldTypeName: TypeName

        if (isJsonNullable(realType)) {
            realType = realType.arguments[0].type!!.resolve()
            isJsonNullable = true
            resolvedFieldTypeName = realType.toTypeName(jsonClass.typeParameters.toTypeParameterResolver())
        } else {
            resolvedFieldTypeName = realType.toTypeName(jsonClass.typeParameters.toTypeParameterResolver())
        }

        val knownType = knownType.detect(realType)
        return if (knownType != null) {
            ReaderFieldType.KnownTypeReaderMeta(realType, resolvedFieldTypeName, isJsonNullable, knownType)
        } else {
            ReaderFieldType.UnknownTypeReaderMeta(realType, resolvedFieldTypeName, isJsonNullable)
        }
    }

    private fun isJsonNullable(type: KSType) = type.declaration is KSClassDeclaration
        && JsonTypes.jsonNullable == (type.declaration as KSClassDeclaration).toClassName()

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
