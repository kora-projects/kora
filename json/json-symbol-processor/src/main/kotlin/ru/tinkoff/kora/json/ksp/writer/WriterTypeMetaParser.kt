package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
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
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.JavaUtils.recordComponents
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.isJavaRecord
import ru.tinkoff.kora.ksp.common.parseAnnotationValue

class WriterTypeMetaParser(resolver: Resolver) {

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
        val accessor = getAccessorMethod(jsonClassDeclaration, field, resolvedType)
        val writer = jsonField?.findValueNoDefault<KSType>("writer")
        val typeMeta = parseWriterFieldType(jsonClassDeclaration, resolvedType)

        val includeType = ((field.findAnnotation(JsonTypes.jsonInclude)
            ?: jsonClassDeclaration.findAnnotation(JsonTypes.jsonInclude))
            ?.arguments?.filter { a -> (a.name?.getShortName() ?: "") == "value" }
            ?.firstNotNullOfOrNull { arg -> JsonClassWriterMeta.IncludeType.tryParse(ClassName.bestGuess(arg.value.toString()).simpleName) }
            ?: JsonClassWriterMeta.IncludeType.NON_NULL)

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
            ?: throw ProcessingErrorException("Can't detect field accessor: $paramName", field)
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
