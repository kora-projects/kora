package io.koraframework.json.ksp.writer

import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import io.koraframework.json.ksp.JsonTypes
import io.koraframework.json.ksp.KnownType
import io.koraframework.json.ksp.findJsonField
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.JavaUtils.recordComponents
import io.koraframework.ksp.common.KspCommonUtils
import io.koraframework.ksp.common.KspCommonUtils.getNameConverter
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.isJavaRecord
import io.koraframework.ksp.common.parseAnnotationValue
import io.koraframework.ksp.common.parseMappingData

class WriterTypeMetaParser(resolver: Resolver) {

    private val knownTypes: KnownType = KnownType(resolver)

    fun parse(declaration: KSClassDeclaration): JsonClassWriterMeta {
        if (declaration.classKind != ClassKind.CLASS) {
            throw ProcessingErrorException(
                """
                JsonWriter can't be generated for type:
                  ${declaration.qualifiedName!!.asString()}

                Problem:
                  @JsonWriter can be generated only for concrete classes and data classes.

                Hint:
                  Interfaces, annotations, enums, primitives, and arrays don't expose object fields that can be written as JSON.

                Fix:
                  Move @JsonWriter/@Json to a concrete class or data class, or provide a custom JsonWriter<${declaration.qualifiedName!!.asString()}> component.
                """.trimIndent(),
                declaration
            )
        }
        if (declaration.modifiers.contains(Modifier.ABSTRACT)) {
            throw ProcessingErrorException(
                """
                JsonWriter can't be generated for abstract type:
                  ${declaration.qualifiedName!!.asString()}

                Problem:
                  Abstract classes don't define a complete concrete JSON shape.

                Hint:
                  Kora can generate writers for concrete classes and data classes. Polymorphic sealed hierarchies must use the supported sealed JSON configuration.

                Fix:
                  Make the type concrete, use a supported sealed hierarchy, or provide a custom JsonWriter<${declaration.qualifiedName!!.asString()}> component.
                """.trimIndent(),
                declaration
            )
        }

        val fieldElements = parseFields(declaration)
        val fieldMetas = mutableListOf<JsonClassWriterMeta.FieldMeta>()
        for (fieldElement in fieldElements) {
            val jsonField = when (fieldElement) {
                is KSFunctionDeclaration -> fieldElement.findJsonField()
                is KSPropertyDeclaration -> fieldElement.findJsonField()
                is KSValueParameter -> fieldElement.findJsonField()
                else -> throw IllegalStateException("Kora internal error: JsonWriter field parser got unsupported declaration type: $fieldElement")
            }
            val fieldMeta = parseField(declaration, fieldElement, jsonField)
            fieldMetas.add(fieldMeta)
        }
        return JsonClassWriterMeta(declaration, fieldMetas)
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
            throw ProcessingErrorException(
                """
                JsonWriter can't resolve field type:
                  ${jsonClassDeclaration.qualifiedName?.asString()}.${field.simpleName.asString()}

                Problem:
                  The field type is reported as an error type by KSP.

                Hint:
                  This usually means the field type is missing from compilation classpath, failed to compile, or is generated in a later processing round.

                Fix:
                  Make sure the field type exists, imports are correct, and all required processors/dependencies are configured.
                """.trimIndent(),
                field
            )
        }
        val jsonName = parseJsonName(field, jsonField, fieldNameConverter)
        val accessor = getAccessorMethod(jsonClassDeclaration, field, resolvedType)

        val writer = field.parseMappingData().getMapping(JsonTypes.jsonWriter)
        val typeMeta = parseWriterFieldType(jsonClassDeclaration, resolvedType)

        val includeType = (field.findAnnotation(JsonTypes.jsonInclude) ?: jsonClassDeclaration.findAnnotation(JsonTypes.jsonInclude))
            ?.findValueNoDefault<KSClassDeclaration>("value")
            ?.let { JsonClassWriterMeta.IncludeType.tryParse(it.simpleName.asString()) }
            ?: JsonClassWriterMeta.IncludeType.NON_NULL

        return JsonClassWriterMeta.FieldMeta(field.simpleName, jsonName, type.resolve(), typeMeta, writer, accessor, includeType)
    }

    private fun getAccessorMethod(jsonClassDeclaration: KSClassDeclaration, field: KSDeclaration, fieldType: KSType): String {
        if (jsonClassDeclaration.isJavaRecord()) {
            return field.simpleName.asString()
        }
        if (field is KSPropertyDeclaration && !field.isPrivate()) {
            return field.simpleName.asString()
        }

        val paramName = field.simpleName.asString()
        val capitalizedParamName = paramName[0].uppercaseChar().toString() + paramName.substring(1)
        return jsonClassDeclaration.getAllFunctions()
            .filter { e -> e.functionKind == FunctionKind.MEMBER }
            .filter { e -> e.parameters.isEmpty() }
            .filter { e ->
                val methodName = e.simpleName.asString()
                methodName == paramName || methodName == "get$capitalizedParamName"
            }
            .filter { m -> m.returnType!!.toTypeName() == fieldType.toTypeName() }
            .map { m -> paramName }
            .firstOrNull()
            ?: throw ProcessingErrorException(
                """
                JsonWriter can't find an accessor for field:
                  ${jsonClassDeclaration.qualifiedName?.asString()}.$paramName

                Problem:
                  No zero-argument method named '$paramName' or 'get$capitalizedParamName' returns the field type.

                Hint:
                  Private fields need a visible accessor so generated JsonWriter code can read the value.

                Fix:
                  Add a public accessor method, make the field accessible, or exclude it with @JsonSkip.
                """.trimIndent(),
                field
            )
    }

    private fun parseWriterFieldType(jsonClass: KSClassDeclaration, resolvedType: KSType): WriterFieldType {
        var realType = if (resolvedType.nullability == Nullability.PLATFORM) {
            resolvedType.makeNullable()
        } else {
            resolvedType
        }

        var isJsonNullable = false
        val resolvedFieldTypeName: TypeName

        if (isJsonNullable(realType)) {
            realType = realType.arguments[0].type!!.resolve()
            isJsonNullable = true
            resolvedFieldTypeName = realType.toTypeName(jsonClass.typeParameters.toTypeParameterResolver())
        } else {
            resolvedFieldTypeName = realType.toTypeName(jsonClass.typeParameters.toTypeParameterResolver())
        }

        val knownType = knownTypes.detect(realType)
        return if (knownType != null) {
            WriterFieldType.KnownWriterFieldType(realType, resolvedFieldTypeName, isJsonNullable, knownType)
        } else {
            WriterFieldType.UnknownWriterFieldType(realType, resolvedFieldTypeName, isJsonNullable)
        }
    }

    private fun isJsonNullable(type: KSType) = type.declaration is KSClassDeclaration
        && JsonTypes.jsonNullable == (type.declaration as KSClassDeclaration).toClassName()

    private fun parseJsonName(param: KSDeclaration, jsonField: KSAnnotation?, nameConverter: KspCommonUtils.NameConverter?): String {
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
