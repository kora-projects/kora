package io.koraframework.openfeature.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.common.util.Either
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.MappingData
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.parseMappingData

object ConfigUtils {

    fun parseFields(resolver: Resolver, element: KSClassDeclaration): Either<List<ConfigField>, List<ProcessingError>> {
        val errors = arrayListOf<ProcessingError>()
        val seen = hashSetOf<String>()
        val fields = arrayListOf<ConfigField>()

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

                    "withContext" -> {
                        if (function.parameters.size == 1) {
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
                    val isNullable = functionType.returnType!!.nullability == Nullability.NULLABLE || function.isAnnotationPresent { it.simpleName == "Nullable" }
                    val mapping = function.parseMappingData().getMapping(ConfigClassNames.configValueExtractor)
                    fields.add(
                        ConfigField(
                            name, functionType.returnType!!.toTypeName().copy(isNullable), isNullable, !function.isAbstract, mapping
                        )
                    )
                }
            }
        }

        val type = element.asType(listOf())
        if (element.classKind == ClassKind.INTERFACE) {
            parseInterfaceFields(type, element)
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
