package ru.tinkoff.kora.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.TagUtils

object ComponentDependencyHelper {
    fun parseDependencyClaim(declaration: ComponentDeclaration): List<DependencyClaim> {
        when (declaration) {
            is ComponentDeclaration.FromModuleComponent -> {
                val element = declaration.method
                val result = ArrayList<DependencyClaim>(declaration.methodParameterTypes.size + 1)
                for (i in 0 until declaration.methodParameterTypes.size) {
                    val parameterType = declaration.methodParameterTypes[i]
                    val parameterElement = element.parameters[i]
                    val tags = TagUtils.parseTagValue(parameterElement)
                    result.add(parseClaim(parameterType, tags))
                }
                return result
            }

            is ComponentDeclaration.AnnotatedComponent -> {
                val element = declaration.constructor
                val result = ArrayList<DependencyClaim>(declaration.methodParameterTypes.size)
                for (i in 0 until declaration.methodParameterTypes.size) {
                    val parameterType = element.parameters[i].type.resolve()
                    val tags = TagUtils.parseTagValue(element.parameters[i])
                    result.add(parseClaim(parameterType, tags))
                }
                return result
            }

            is ComponentDeclaration.DiscoveredAsDependencyComponent -> {
                val element = declaration.constructor
                val result = ArrayList<DependencyClaim>(element.parameters.size)
                for (parameter in element.parameters) {
                    val type = parameter.type.resolve()
                    val tags = TagUtils.parseTagValue(parameter)
                    result.add(parseClaim(type, tags))
                }
                return result
            }

            is ComponentDeclaration.FromExtensionComponent -> {
                val element = declaration.sourceMethod
                val result = ArrayList<DependencyClaim>(element.parameters.size)
                for (parameter in element.parameters) {
                    val type = parameter.type.resolve()
                    val tags = TagUtils.parseTagValue(parameter)
                    result.add(parseClaim(type, tags))
                }
                return result
            }

            else -> throw IllegalArgumentException()
        }
    }


    private fun parseClaim(parameterType: KSType, tags: Set<String>): DependencyClaim {
        val typeName = parameterType.toTypeName()
        if (typeName is ParameterizedTypeName) {
            val firstTypeParam = parameterType.arguments[0].type!!.resolve()
            if (typeName.rawType == CommonClassNames.typeRef) {
                return DependencyClaim(firstTypeParam, tags, DependencyClaim.DependencyClaimType.TYPE_REF)
            }
            if (typeName.rawType == CommonClassNames.all) {
                val allOf = typeName.typeArguments[0]
                if (allOf is ParameterizedTypeName) {
                    if (allOf.rawType == CommonClassNames.valueOf) {
                        return DependencyClaim(firstTypeParam.arguments[0].type!!.resolve(), tags, DependencyClaim.DependencyClaimType.ALL_OF_VALUE)
                    }
                    if (allOf.rawType == CommonClassNames.promiseOf) {
                        return DependencyClaim(firstTypeParam.arguments[0].type!!.resolve(), tags, DependencyClaim.DependencyClaimType.ALL_OF_PROMISE)
                    }
                }
                return DependencyClaim(firstTypeParam, tags, DependencyClaim.DependencyClaimType.ALL)
            }
            if (typeName.rawType == CommonClassNames.valueOf) {
                if (parameterType.isMarkedNullable) {
                    return DependencyClaim(firstTypeParam, tags, DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF)
                } else {
                    return DependencyClaim(firstTypeParam, tags, DependencyClaim.DependencyClaimType.VALUE_OF)
                }
            }
            if (typeName.rawType == CommonClassNames.promiseOf) {
                if (parameterType.isMarkedNullable) {
                    return DependencyClaim(firstTypeParam, tags, DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF)
                } else {
                    return DependencyClaim(firstTypeParam, tags, DependencyClaim.DependencyClaimType.PROMISE_OF)
                }
            }
        }
        if (parameterType.isMarkedNullable) {
            return DependencyClaim(parameterType, tags, DependencyClaim.DependencyClaimType.NULLABLE_ONE)
        } else {
            return DependencyClaim(parameterType, tags, DependencyClaim.DependencyClaimType.ONE_REQUIRED)
        }
    }
}
