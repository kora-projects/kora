package ru.tinkoff.kora.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.TagUtils
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import javax.tools.Diagnostic

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
                    result.add(parseClaim(parameterType, tags, declaration.method.parameters[i]))
                }
                return result
            }

            is ComponentDeclaration.AnnotatedComponent -> {
                val element = declaration.constructor
                val result = ArrayList<DependencyClaim>(declaration.methodParameterTypes.size)
                for (i in 0 until declaration.methodParameterTypes.size) {
                    val parameterType = declaration.methodParameterTypes[i]
                    val tags = TagUtils.parseTagValue(element.parameters[i])
                    result.add(parseClaim(parameterType, tags, declaration.constructor.parameters[i]))
                }
                return result
            }

            is ComponentDeclaration.DiscoveredAsDependencyComponent -> {
                val element = declaration.constructor
                val constructorParameterTypes = if (declaration.classDeclaration.typeParameters.isEmpty()) {
                    element.parameters.asSequence().map { it.type.resolve() }
                } else {
                    // this will fail but there's nothing we can do about it
                    element.asMemberOf(declaration.type).parameterTypes.asSequence().mapNotNull { it!! }
                }
                val result = ArrayList<DependencyClaim>(element.parameters.size)
                for ((parameter, parameterType) in element.parameters.asSequence().zip(constructorParameterTypes)) {
                    val tags = TagUtils.parseTagValue(parameter)
                    result.add(parseClaim(parameterType, tags, element))
                }
                return result
            }

            is ComponentDeclaration.FromExtensionComponent -> {
                val result = ArrayList<DependencyClaim>(declaration.methodParameterTypes.size)
                for ((type, tags) in declaration.methodParameterTypes.zip(declaration.methodParameterTags)) {
                    result.add(parseClaim(type, tags, declaration.source))
                }
                return result
            }

            else -> throw IllegalArgumentException()
        }
    }


    fun parseClaim(parameterType: KSType, tag: String?, element: KSAnnotated): DependencyClaim {
        if (parameterType.isError) {
            throw ProcessingErrorException(ProcessingError("Dependency type parameter is not resolvable in the current round of processing: $element\nTry disabling Kora KSP processor dependency and compile without it to check for errors in your codebase (Kotlin and KSP compiler work only this way)", element, Diagnostic.Kind.WARNING))
        }
        val typeName = try {
            parameterType.toTypeName()
        } catch (e: IllegalArgumentException) {
            throw ProcessingErrorException(ProcessingError("Dependency type parameter is not resolvable in the current round of processing: $element\nTry disabling Kora KSP processor dependency and compile without it to check for errors in your codebase (Kotlin and KSP compiler work only this way)", element, Diagnostic.Kind.WARNING))
        }
        if (typeName is ParameterizedTypeName) {
            val firstTypeParam = parameterType.arguments[0].type!!.resolve()
            if (typeName.rawType == CommonClassNames.typeRef) {
                return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.TYPE_REF)
            }
            if (typeName.rawType == CommonClassNames.all) {
                val allOf = typeName.typeArguments[0]
                if (allOf is ParameterizedTypeName) {
                    if (allOf.rawType == CommonClassNames.valueOf) {
                        return DependencyClaim(firstTypeParam.arguments[0].type!!.resolve(), tag, DependencyClaim.DependencyClaimType.ALL_OF_VALUE)
                    }
                    if (allOf.rawType == CommonClassNames.promiseOf) {
                        return DependencyClaim(firstTypeParam.arguments[0].type!!.resolve(), tag, DependencyClaim.DependencyClaimType.ALL_OF_PROMISE)
                    }
                }
                return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.ALL)
            }
            if (typeName.rawType == CommonClassNames.valueOf) {
                if (parameterType.isMarkedNullable || element.isAnnotationPresent(CommonClassNames.nullable)) {
                    return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF)
                } else {
                    return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.VALUE_OF)
                }
            }
            if (typeName.rawType == CommonClassNames.promiseOf) {
                if (parameterType.isMarkedNullable || element.isAnnotationPresent(CommonClassNames.nullable)) {
                    return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF)
                } else {
                    return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.PROMISE_OF)
                }
            }
        }
        if (parameterType.isMarkedNullable || element.isAnnotationPresent(CommonClassNames.nullable)) {
            return DependencyClaim(parameterType, tag, DependencyClaim.DependencyClaimType.NULLABLE_ONE)
        } else {
            return DependencyClaim(parameterType, tag, DependencyClaim.DependencyClaimType.ONE_REQUIRED)
        }
    }
}
