package io.koraframework.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.kora.app.ksp.declaration.ModuleDeclaration
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.TagUtils
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException
import javax.tools.Diagnostic

object ComponentDependencyHelper {
    fun parseDependencyClaim(declaration: ComponentDeclaration): List<DependencyClaim> {
        when (declaration) {
            is ComponentDeclaration.FromModuleComponent -> {
                val element = declaration.method
                val result = ArrayList<DependencyClaim>(declaration.methodParameterTypes.size + 1)
                if (declaration.module is ModuleDeclaration.FactoryModule) {
                    result.add(
                        DependencyClaim(
                            declaration.module.element.asType(listOf()),
                            declaration.module.tag,
                            DependencyClaim.DependencyClaimType.ONE_REQUIRED,
                            element
                        )
                    )
                }
                if (declaration.module is ModuleDeclaration.ClassModule) {
                    result.add(
                        DependencyClaim(
                            declaration.module.element.asType(listOf()),
                            null,
                            DependencyClaim.DependencyClaimType.ONE_REQUIRED,
                            element
                        )
                    )
                }
                for (i in 0 until declaration.methodParameterTypes.size) {
                    val parameterType = declaration.methodParameterTypes[i]
                    val parameterElement = element.parameters[i]
                    var tag = TagUtils.parseTagValue(parameterElement)
                    if (CommonClassNames.tagFactory.canonicalName == tag) {
                        if (declaration.module is ModuleDeclaration.FactoryModule) {
                            tag = declaration.module.tag
                        } else {
                            throw ProcessingErrorException(
                                """
                                @Tag.Factory can only be used inside factory modules.

                                Fix:
                                  - Move this provider to a factory module.
                                  - Replace @Tag.Factory with an explicit @Tag(...) value.
                                """.trimIndent(),
                                declaration.method
                            )
                        }
                    }
                    result.add(parseClaim(parameterType, tag, declaration.method.parameters[i]))
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

            is ComponentDeclaration.FromExtensionComponent -> {
                val result = ArrayList<DependencyClaim>(declaration.methodParameterTypes.size)
                for ((type, tags) in declaration.methodParameterTypes.zip(declaration.methodParameterTags)) {
                    result.add(parseClaim(type, tags, declaration.source))
                }
                return result
            }


            is ComponentDeclaration.OptionalComponent ->
                throw IllegalStateException("Kora internal error: optional synthetic component cannot declare dependencies: $declaration")
            is ComponentDeclaration.PromisedProxyComponent ->
                throw IllegalStateException("Kora internal error: promised proxy synthetic component cannot declare dependencies: $declaration")
        }
    }


    fun parseClaim(parameterType: KSType, tag: String?, element: KSAnnotated): DependencyClaim {
        if (parameterType.isError) {
            throw ProcessingErrorException(
                ProcessingError(
                    """
                    Dependency type cannot be resolved in the current KSP round:
                      element: $element

                    Fix:
                      - Check imports and module dependencies.
                      - Compile without Kora symbol processors to expose earlier Kotlin errors if KSP hides them.
                    """.trimIndent(),
                    element,
                    Diagnostic.Kind.WARNING
                )
            )
        }
        val typeName = try {
            parameterType.toTypeName()
        } catch (e: IllegalArgumentException) {
            throw ProcessingErrorException(
                ProcessingError(
                    """
                    Dependency type cannot be converted to a KotlinPoet type in the current KSP round:
                      element: $element

                    Fix:
                      - Check imports and module dependencies.
                      - Compile without Kora symbol processors to expose earlier Kotlin errors if KSP hides them.
                    """.trimIndent(),
                    element,
                    Diagnostic.Kind.WARNING
                )
            )
        }
        if (typeName == CommonClassNames.graph || typeName == CommonClassNames.refreshableGraph) {
            return DependencyClaim(parameterType, tag, DependencyClaim.DependencyClaimType.GRAPH, element)
        }
        if (typeName is ParameterizedTypeName) {
            val firstTypeParam = parameterType.arguments[0].type!!.resolve()
            if (typeName.rawType == CommonClassNames.typeRef) {
                return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.TYPE_REF, element)
            }
            if (typeName.rawType.canonicalName == CommonClassNames.node.canonicalName) {
                if (firstTypeParam.isMarkedNullable) {
                    throw ProcessingErrorException(
                        """
                        Invalid Node dependency argument:
                          Node<T> cannot use a nullable T.

                        Fix:
                          - Use a non-nullable Node<T>.
                          - Inject nullable dependency directly if nullable access is required.
                        """.trimIndent(),
                        element
                    )
                }
                return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.NODE_OF, element);
            }

            if (typeName.rawType == CommonClassNames.all) {
                val allOf = typeName.typeArguments[0]
                if (allOf is ParameterizedTypeName) {
                    if (allOf.rawType == CommonClassNames.valueOf) {
                        return DependencyClaim(firstTypeParam.arguments[0].type!!.resolve(), tag, DependencyClaim.DependencyClaimType.ALL_OF_VALUE, element)
                    }
                    if (allOf.rawType == CommonClassNames.promiseOf) {
                        return DependencyClaim(firstTypeParam.arguments[0].type!!.resolve(), tag, DependencyClaim.DependencyClaimType.ALL_OF_PROMISE, element)
                    }
                }
                return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.ALL, element)
            }
            if (typeName.rawType == CommonClassNames.valueOf) {
                if (parameterType.isMarkedNullable || element.isAnnotationPresent(CommonClassNames.nullable)) {
                    return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF, element)
                } else {
                    return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.VALUE_OF, element)
                }
            }
            if (typeName.rawType == CommonClassNames.promiseOf) {
                if (parameterType.isMarkedNullable || element.isAnnotationPresent(CommonClassNames.nullable)) {
                    return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF, element)
                } else {
                    return DependencyClaim(firstTypeParam, tag, DependencyClaim.DependencyClaimType.PROMISE_OF, element)
                }
            }
        }
        if (parameterType.isMarkedNullable || element.isAnnotationPresent(CommonClassNames.nullable)) {
            return DependencyClaim(parameterType, tag, DependencyClaim.DependencyClaimType.NULLABLE_ONE, element)
        } else {
            return DependencyClaim(parameterType, tag, DependencyClaim.DependencyClaimType.ONE_REQUIRED, element)
        }
    }
}
