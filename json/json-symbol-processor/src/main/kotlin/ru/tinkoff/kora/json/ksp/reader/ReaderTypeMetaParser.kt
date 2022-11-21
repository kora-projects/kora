package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.common.annotation.JsonField
import ru.tinkoff.kora.json.common.annotation.JsonReader
import ru.tinkoff.kora.json.ksp.KnownType
import ru.tinkoff.kora.json.ksp.discriminator
import ru.tinkoff.kora.json.ksp.isSealed
import ru.tinkoff.kora.json.ksp.writer.JsonClassWriterMeta
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.isJavaRecord
import ru.tinkoff.kora.ksp.common.parseAnnotationValue

@KspExperimental
class ReaderTypeMetaParser(
    resolver: Resolver,
    private val knownType: KnownType,
    private val logger: KSPLogger
) {

    private val defaultReader = resolver.getClassDeclarationByName(JsonField.DefaultReader::class.qualifiedName!!)!!.asType(listOf())
    private val jsonFieldAnnotation = resolver.getClassDeclarationByName(JsonField::class.qualifiedName!!)!!
    private val enumType = resolver.getClassDeclarationByName<Enum<*>>()!!.asStarProjectedType()

    fun parse(typeReference: KSTypeReference, discriminatorRequired: Boolean = false): JsonClassReaderMeta? {
        val type = typeReference.resolve()
        val declaration = when (type.declaration) {
            is KSTypeAlias -> (type.declaration as KSTypeAlias).type.resolve().declaration as KSClassDeclaration
            is KSClassDeclaration -> type.declaration as KSClassDeclaration
            else -> return null
        }
        val discriminator = if (discriminatorRequired) {
            discriminator(declaration)
        } else {
            null
        }
        if (isSealed(declaration)) {
            return JsonClassReaderMeta(
                type,
                typeReference,
                emptyList(),
                discriminator,
                true,
            )
        }
        if (enumType.isAssignableFrom(type)) {
            return JsonClassReaderMeta(type, typeReference, emptyList(), null, false)
        }

        val jsonConstructor = this.findJsonConstructor(declaration)
        if (jsonConstructor == null) {
            logger.error(
                "Class: %s\nTo generate json reader class must have one public constructor or constructor annotated with any of @Json/@JsonReader".format(declaration.toClassName()),
                typeReference
            )
            return null
        }
        val fields = mutableListOf<JsonClassReaderMeta.FieldMeta>()

        val nameConverter = declaration.getNameConverter()
        for (parameter in jsonConstructor.parameters) {
            val fieldMeta: JsonClassReaderMeta.FieldMeta? = parseField(declaration, parameter, nameConverter)
            if (fieldMeta == null) {
                return null
            } else {
                fields.add(fieldMeta)
            }
        }

        return JsonClassReaderMeta(type, typeReference, fields, discriminator, false)
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
            .filter { it.isAnnotationPresent(JsonReader::class) || it.isAnnotationPresent(Json::class) }
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

    private fun parseField(jsonClass: KSClassDeclaration, parameter: KSValueParameter, nameConverter: NameConverter?): JsonClassReaderMeta.FieldMeta? {
        val jsonField = this.findJsonField(parameter, jsonClass)
        val resolvedParameterType = parameter.type.resolve()
        if (resolvedParameterType.isError) {
            logger.error("Field %s.%s is ERROR".format(jsonClass, parameter.name!!.asString()), parameter)
            return null
        }
        val jsonName = parseJsonName(parameter, jsonField, nameConverter)


        val reader = when (val rdr = jsonField?.let { parseAnnotationValue<KSType>(it, "reader") }) {
            null -> {
                null
            }
            defaultReader -> {
                null
            }
            else -> {
                rdr
            }
        }
        val typeMeta: ReaderFieldType = this.parseReaderFieldType(parameter.type)
        return JsonClassReaderMeta.FieldMeta(parameter, jsonName, resolvedParameterType, typeMeta, reader)
    }

    private fun parseReaderFieldType(fieldType: KSTypeReference): ReaderFieldType {
        val resolvedFieldType = fieldType.resolve()
        val knownType = knownType.detect(resolvedFieldType)
        return if (knownType != null) {
            ReaderFieldType.KnownTypeReaderMeta(knownType, fieldType)
        } else {
            ReaderFieldType.UnknownTypeReaderMeta(fieldType)
        }
    }

    private fun findJsonField(param: KSValueParameter, jsonClass: KSClassDeclaration): KSAnnotation? {
        if (jsonClass.isJavaRecord()) {
            val relatedGetter = jsonClass.getAllFunctions().firstOrNull { it.simpleName.asString() == param.name!!.asString() }
            return relatedGetter?.annotations?.firstOrNull { it.annotationType.resolve() == jsonFieldAnnotation.asStarProjectedType() }
        }
        return param.annotations.firstOrNull { it.annotationType.resolve() == jsonFieldAnnotation.asStarProjectedType() }
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
        return if (jsonFieldValue != null && jsonFieldValue.isNotBlank()) {
            jsonFieldValue
        } else param.name!!.asString()
    }

}
