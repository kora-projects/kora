package ru.tinkoff.kora.config.ksp

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.common.util.Either
import ru.tinkoff.kora.ksp.common.JavaUtils.isRecord
import ru.tinkoff.kora.ksp.common.JavaUtils.recordComponents
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.parseMappingData

object ConfigUtils {
    fun parseFields(resolver: Resolver, element: KSClassDeclaration): Either<List<ConfigField>, List<ProcessingError>> {
        val errors = arrayListOf<ProcessingError>()
        val seen = hashSetOf<String>()
        val fields = arrayListOf<ConfigField>()

        fun parseRecordFields(type: KSType, typeDecl: KSClassDeclaration) {
            require(typeDecl.isRecord()) { "should be record" }
            for (recordComponent in typeDecl.recordComponents()) {
                val recordComponentType = recordComponent.asMemberOf(type)
                val name = recordComponent.simpleName.asString()
                if (seen.add(name)) {
                    val isNullable = recordComponentType.isMarkedNullable
                    val mapping = recordComponent.parseMappingData().getMapping(ConfigClassNames.configValueExtractor)
                    fields.add(
                        ConfigField(
                            name, recordComponentType.toTypeName(), isNullable, false, mapping
                        )
                    )
                }
            }
        }

        fun parsePojoFields(type: KSType, typeDecl: KSClassDeclaration) {
            require(typeDecl.classKind == ClassKind.CLASS) { "Method expecting class" }
            if (typeDecl.modifiers.contains(Modifier.ABSTRACT)) {
                errors.add(ProcessingError("Config annotated class can't be abstract", typeDecl))
                return
            }
            var equals: KSFunctionDeclaration? = null
            var hashCode: KSFunctionDeclaration? = null
            class FieldAndAccessors(var property: KSPropertyDeclaration? = null, var getter: KSFunctionDeclaration? = null, var setter: KSFunctionDeclaration? = null)
            val propertyMap = hashMapOf<String, FieldAndAccessors>()
            for (function in typeDecl.getDeclaredFunctions()) {
                val name = function.simpleName.asString()
                if (function.modifiers.contains(Modifier.PRIVATE)) {
                    continue
                }
                if (name == "equals" && function.parameters.size == 1) {
                    equals = function
                } else if (name == "hashCode" && function.parameters.isEmpty()) {
                    hashCode = function
                } else {
                    if (name.startsWith("get")) {
                        val propertyName = name.substring(3).replaceFirstChar { it.lowercaseChar() }
                        propertyMap.computeIfAbsent(propertyName) {FieldAndAccessors()}.getter = function
                    }
                    if (name.startsWith("set")) {
                        val propertyName = name.substring(3).replaceFirstChar { it.lowercaseChar() }
                        propertyMap.computeIfAbsent(propertyName) {FieldAndAccessors()}.setter = function
                    }
                }
            }
            for (property in typeDecl.getAllProperties()) {
                propertyMap.computeIfAbsent(property.simpleName.asString()) {FieldAndAccessors()}.property = property
            }
            val properties = propertyMap.values.asSequence().filter { it.setter != null && it.getter != null && it.property != null }.map { it.property!! }.toList()
            if (equals == null || hashCode == null) {
                errors.add(ProcessingError("Config annotated class must override equals and hashCode methods", typeDecl))
                return
            }
            properties.forEach {
                val fieldType = it.asMemberOf(type)
                if (seen.add(it.simpleName.asString())) {
                    val isNullable = fieldType.isMarkedNullable
                    val mapping = it.parseMappingData().getMapping(ConfigClassNames.configValueExtractor)
                    fields.add(ConfigField(it.simpleName.asString(), fieldType.toTypeName(), isNullable, true, mapping))
                }
            }
        }

        fun parseDataClassFields(type: KSType, typeDecl: KSClassDeclaration) {
            require(typeDecl.classKind == ClassKind.CLASS) { "Method expecting class" }
            require(typeDecl.modifiers.contains(Modifier.DATA))
            if (typeDecl.modifiers.contains(Modifier.ABSTRACT)) {
                errors.add(ProcessingError("Config annotated class can't be abstract", typeDecl))
                return
            }
            val primaryConstructor = typeDecl.primaryConstructor!!
            for (parameter in primaryConstructor.parameters) {
                val name = parameter.name!!.asString()
                val fieldType = parameter.type.resolve()
                val isNullable = fieldType.isMarkedNullable
                val mapping = parameter.parseMappingData().getMapping(ConfigClassNames.configValueExtractor)
                fields.add(ConfigField(name, fieldType.toTypeName(), isNullable, false, mapping))
            }
        }

        fun parseInterfaceFields(type: KSType, typeDecl: KSClassDeclaration) {
            require(typeDecl.classKind == ClassKind.INTERFACE) { "Method expecting interface" }
            for (function in typeDecl.getAllFunctions()) {
                when (function.simpleName.asString()) {
                    "equals" -> {
                        if (function.parameters.size == 1) {
                            continue
                        }
                    }

                    "hashCode" -> {
                        if (function.parameters.isEmpty()) {
                            continue
                        }
                    }

                    "toString" -> {
                        if (function.parameters.isEmpty()) {
                            continue
                        }
                    }

                }
                if (function.parameters.isNotEmpty()) {
                    if (!function.isAbstract) {
                        // todo are default java functions abstract?
                        continue
                    } else {
                        errors.add(ProcessingError("Config has non default method with arguments: ${function.simpleName.asString()}", function))
                        return
                    }
                }
                if (function.returnType == resolver.builtIns.unitType) {
                    if (!function.isAbstract) {
                        continue
                    }
                    errors.add(ProcessingError("Config has non default method returning void", function))
                    return
                }
                if (function.typeParameters.isNotEmpty()) {
                    errors.add(ProcessingError("Config has method with type parameters", function))
                    return
                }
                val functionType = function.asMemberOf(type)
                val name = function.simpleName.asString()
                if (seen.add(name)) {
                    val isNullable = functionType.returnType!!.isMarkedNullable
                    val mapping = function.parseMappingData().getMapping(ConfigClassNames.configValueExtractor)
                    fields.add(
                        ConfigField(
                            name, functionType.returnType!!.toTypeName(), isNullable, !function.isAbstract, mapping
                        )
                    )
                }
            }
        }

        val type = element.asType(listOf())
        if (element.isRecord()) {
            parseRecordFields(type, element)
        } else if (element.classKind == ClassKind.INTERFACE) {
            parseInterfaceFields(type, element)
        } else if (element.classKind == ClassKind.CLASS && element.modifiers.contains(Modifier.DATA)) {
            parseDataClassFields(type, element)
        } else if (element.classKind == ClassKind.CLASS && element.getConstructors().any { it.modifiers.contains(Modifier.PUBLIC) && it.parameters.isEmpty() }) {
            parsePojoFields(type, element)
        } else {
            return Either.right(
                listOf(
                    ProcessingError(
                        "typeElement should be interface, data class or java record, got " + element.classKind,
                        element
                    )
                )
            )
        }
        return if (errors.isEmpty()) {
            Either.left(fields)
        } else {
            Either.right(errors)
        }
    }

    data class ConfigField(val name: String, val typeName: TypeName, val isNullable: Boolean, val hasDefault: Boolean, val mapping: MappingData?)
}
