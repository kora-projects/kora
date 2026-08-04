package io.koraframework.config.ksp

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.Either
import io.koraframework.ksp.common.JavaUtils.isRecord
import io.koraframework.ksp.common.JavaUtils.recordComponents
import io.koraframework.ksp.common.MappingData
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.parseMappingData

object ConfigUtils {
    fun parseFields(resolver: Resolver, element: KSClassDeclaration): Either<List<ConfigField>, List<ProcessingError>> {
        val errors = arrayListOf<ProcessingError>()
        val seen = hashSetOf<String>()
        val fields = arrayListOf<ConfigField>()

        fun parseRecordFields(type: KSType, typeDecl: KSClassDeclaration) {
            require(typeDecl.isRecord()) { internalExpectedConfigKindError(typeDecl, "record") }
            for (recordComponent in typeDecl.recordComponents()) {
                val recordComponentType = recordComponent.asMemberOf(type).returnType!!
                val name = recordComponent.simpleName.asString()
                if (seen.add(name)) {
                    val isNullable = recordComponentType.isMarkedNullable
                    val mapping = recordComponent.parseMappingData().getMapping(ConfigClassNames.configValueMapper)
                    fields.add(
                        ConfigField(
                            name, recordComponentType.toTypeName(), isNullable, false, false, mapping
                        )
                    )
                }
            }
        }

        fun parsePojoFields(type: KSType, typeDecl: KSClassDeclaration) {
            require(typeDecl.classKind == ClassKind.CLASS) { internalExpectedConfigKindError(typeDecl, "class") }
            if (typeDecl.modifiers.contains(Modifier.ABSTRACT)) {
                errors.add(ProcessingError(configAbstractClassError(typeDecl), typeDecl))
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
                        propertyMap.computeIfAbsent(propertyName) { FieldAndAccessors() }.getter = function
                    } else if (name.startsWith("set")) {
                        val propertyName = name.substring(3).replaceFirstChar { it.lowercaseChar() }
                        propertyMap.computeIfAbsent(propertyName) { FieldAndAccessors() }.setter = function
                    } else {
                        propertyMap.computeIfAbsent(name) { FieldAndAccessors() }.getter = function
                    }
                }
            }
            for (property in typeDecl.getAllProperties()) {
                propertyMap.computeIfAbsent(property.simpleName.asString()) { FieldAndAccessors() }.property = property
            }
            val properties = propertyMap.values.asSequence().filter { it.setter != null && it.getter != null && it.property != null }.map { it.property!! }.toList()
            if (equals == null || hashCode == null) {
                errors.add(ProcessingError(configEqualsHashCodeError(typeDecl), typeDecl))
                return
            }
            properties.forEach {
                val fieldType = it.asMemberOf(type)
                if (seen.add(it.simpleName.asString())) {
                    val isNullable = fieldType.isMarkedNullable
                    val mapping = it.parseMappingData().getMapping(ConfigClassNames.configValueMapper)
                    fields.add(ConfigField(it.simpleName.asString(), fieldType.toTypeName(), isNullable, true, false, mapping))
                }
            }
        }

        fun parseDataClassFields(typeDecl: KSClassDeclaration) {
            require(typeDecl.classKind == ClassKind.CLASS) { internalExpectedConfigKindError(typeDecl, "class") }
            require(typeDecl.modifiers.contains(Modifier.DATA)) { internalExpectedConfigKindError(typeDecl, "data class") }
            if (typeDecl.modifiers.contains(Modifier.ABSTRACT)) {
                errors.add(ProcessingError(configAbstractClassError(typeDecl), typeDecl))
                return
            }
            val primaryConstructor = typeDecl.primaryConstructor!!
            for (parameter in primaryConstructor.parameters) {
                val name = parameter.name!!.asString()
                val fieldType = parameter.type.resolve()
                val isNullable = fieldType.isMarkedNullable
                val mapping = parameter.parseMappingData().getMapping(ConfigClassNames.configValueMapper)
                fields.add(ConfigField(name, fieldType.toTypeName(), isNullable, parameter.hasDefault, false, mapping))
            }
        }

        fun parseInterfaceFields(type: KSType, typeDecl: KSClassDeclaration) {
            require(typeDecl.classKind == ClassKind.INTERFACE) { internalExpectedConfigKindError(typeDecl, "interface") }
            for (property in typeDecl.getAllProperties()) {
                val name = property.simpleName.asString()
                val propertyType = property.type.resolve()
                val isNullable = propertyType.nullability == Nullability.NULLABLE
                val mapping = property.parseMappingData().getMapping(ConfigClassNames.configValueMapper)
                fields.add(
                    ConfigField(
                        name, propertyType.toTypeName().copy(isNullable), isNullable, false, true, mapping
                    )
                )
            }
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
                        errors.add(ProcessingError(configMethodWithArgumentsError(function), function))
                        return
                    }
                }
                if (function.returnType == resolver.builtIns.unitType) {
                    if (!function.isAbstract) {
                        continue
                    }
                    errors.add(ProcessingError(configVoidMethodError(function), function))
                    return
                }
                if (function.typeParameters.isNotEmpty()) {
                    errors.add(ProcessingError(configGenericMethodError(function), function))
                    return
                }
                val functionType = function.asMemberOf(type)
                val name = function.simpleName.asString()
                if (seen.add(name)) {
                    val isNullable = functionType.returnType!!.nullability == Nullability.NULLABLE || function.isAnnotationPresent { it.simpleName == "Nullable" }
                    val mapping = function.parseMappingData().getMapping(ConfigClassNames.configValueMapper)
                    fields.add(
                        ConfigField(
                            name, functionType.returnType!!.toTypeName().copy(isNullable), isNullable, !function.isAbstract, false, mapping
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
            parseDataClassFields(element)
        } else if (element.classKind == ClassKind.CLASS && element.getConstructors().any { it.modifiers.contains(Modifier.PUBLIC) && it.parameters.isEmpty() }) {
            parsePojoFields(type, element)
        } else {
            return Either.right(
                listOf(
                    ProcessingError(
                        invalidConfigTypeError(element),
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

    data class ConfigField(val name: String, val typeName: TypeName, val isNullable: Boolean, val hasDefault: Boolean, val isVal: Boolean, val mapping: MappingData?)

    private fun invalidConfigTypeError(element: KSClassDeclaration): String {
        return """
            Invalid config mapper target: `${element.qualifiedName?.asString()}`.

            Config mapping can be generated only for interfaces, data classes, Java records, or JavaBean-style classes with a public empty constructor.
            Actual declaration kind: `${element.classKind}`.

            Fix: move the annotation to a supported config DTO type.
        """.trimIndent()
    }

    private fun configMethodWithArgumentsError(function: KSFunctionDeclaration): String {
        return """
            Invalid config interface method: `${function.simpleName.asString()}`.

            Non-default config methods must not have parameters because they describe config fields.

            Fix: remove method parameters, make the method default, or move this logic outside the config interface.
        """.trimIndent()
    }

    private fun configVoidMethodError(function: KSFunctionDeclaration): String {
        return """
            Invalid config interface method: `${function.simpleName.asString()}`.

            Non-default config methods must return a value because they describe config fields.

            Fix: change the return type to the config field type, or make the method default.
        """.trimIndent()
    }

    private fun configGenericMethodError(function: KSFunctionDeclaration): String {
        return """
            Invalid config interface method: `${function.simpleName.asString()}`.

            Config field methods cannot declare type parameters.

            Fix: use a concrete return type for this config field, or move generic helper logic to a default method.
        """.trimIndent()
    }

    private fun configAbstractClassError(typeDecl: KSClassDeclaration): String {
        return """
            Invalid config class: `${typeDecl.qualifiedName?.asString()}`.

            Config classes must be instantiable, but this class is abstract.

            Fix: remove `abstract`, use an interface, or provide a concrete config DTO class.
        """.trimIndent()
    }

    private fun configEqualsHashCodeError(typeDecl: KSClassDeclaration): String {
        return """
            Invalid config class: `${typeDecl.qualifiedName?.asString()}`.

            JavaBean-style config classes must override both `equals` and `hashCode` so generated config mapping can compare defaults and values reliably.

            Fix: implement `equals` and `hashCode`, use a data class/record, or use an interface config declaration.
        """.trimIndent()
    }

    private fun internalExpectedConfigKindError(typeDecl: KSClassDeclaration, expectedKind: String): String {
        return """
            Kora internal error: config parser helper expected `$expectedKind`, but received `${typeDecl.classKind}`.

            Actual declaration: `${typeDecl.qualifiedName?.asString()}`
            Please report this with the annotated config type.
        """.trimIndent()
    }
}
