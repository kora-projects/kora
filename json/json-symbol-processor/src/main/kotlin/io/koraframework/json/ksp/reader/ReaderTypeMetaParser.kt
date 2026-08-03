package io.koraframework.json.ksp.reader

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import io.koraframework.json.ksp.JsonTypes
import io.koraframework.json.ksp.KnownType
import io.koraframework.json.ksp.findJsonField
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.KspCommonUtils
import io.koraframework.ksp.common.KspCommonUtils.getNameConverter
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.parseAnnotationValue
import io.koraframework.ksp.common.parseMappingData

class ReaderTypeMetaParser(
    private val knownType: KnownType,
    private val logger: KSPLogger
) {

    fun parse(declaration: KSClassDeclaration): JsonClassReaderMeta {
        if (declaration.classKind != ClassKind.CLASS) {
            throw ProcessingErrorException(
                """
                JsonReader can't be generated for type:
                  ${declaration.qualifiedName!!.asString()}

                Problem:
                  @JsonReader can be generated only for concrete classes and data classes.

                Hint:
                  Interfaces, annotations, enums, primitives, and arrays don't have a constructor that can be used to create an object from JSON.

                Fix:
                  Move @JsonReader/@Json to a concrete class or data class, or provide a custom JsonReader<${declaration.qualifiedName!!.asString()}> component.
                """.trimIndent(),
                declaration
            )
        }
        if (declaration.modifiers.contains(Modifier.ABSTRACT)) {
            throw ProcessingErrorException(
                """
                JsonReader can't be generated for abstract type:
                  ${declaration.qualifiedName!!.asString()}

                Problem:
                  Abstract classes can't be instantiated while reading JSON.

                Hint:
                  Kora can generate readers for concrete classes and data classes. Polymorphic sealed hierarchies must use the supported sealed JSON configuration.

                Fix:
                  Make the type concrete, use a supported sealed hierarchy, or provide a custom JsonReader<${declaration.qualifiedName!!.asString()}> component.
                """.trimIndent(),
                declaration
            )
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
                jsonConstructorError(classDeclaration, "No public constructor was found."),
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
                jsonConstructorError(classDeclaration, "More than one public constructor is annotated with @JsonReader."),
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
                jsonConstructorError(classDeclaration, "More than one public constructor is annotated with @Json."),
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
            jsonConstructorError(classDeclaration, "There are multiple possible public constructors and none is selected explicitly."),
            classDeclaration
        )
    }

    private fun jsonConstructorError(classDeclaration: KSClassDeclaration, problem: String): String {
        return """
            JsonReader can't choose a constructor for type:
              ${classDeclaration.toClassName()}

            Problem:
              $problem

            Hint:
              JsonReader generation needs exactly one constructor to create the object.

            Fix:
              Keep a single public constructor, or mark exactly one public constructor with @JsonReader or @Json.
        """.trimIndent()
    }

    private fun parseField(jsonClass: KSClassDeclaration, parameter: KSValueParameter, jsonField: KSAnnotation?, nameConverter: KspCommonUtils.NameConverter?): JsonClassReaderMeta.FieldMeta {
        val jsonName = parseJsonName(parameter, jsonField, nameConverter)
        val reader = parameter.parseMappingData().getMapping(JsonTypes.jsonReader)
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

    private fun parseJsonName(param: KSValueParameter, jsonField: KSAnnotation?, nameConverter: KspCommonUtils.NameConverter?): String {
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
