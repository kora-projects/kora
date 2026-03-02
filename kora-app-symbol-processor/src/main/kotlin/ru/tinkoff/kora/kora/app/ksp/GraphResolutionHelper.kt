package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependency.*
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim.DependencyClaimType.*
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponents
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclarations
import ru.tinkoff.kora.kora.app.ksp.declaration.DeclarationWithIndex


object GraphResolutionHelper {
    fun findDependencyDeclarations(
        ctx: ProcessingContext,
        componentDeclarations: ComponentDeclarations,
        dependencyClaim: DependencyClaim
    ): List<DeclarationWithIndex> {
        val declarations = componentDeclarations.getByType(dependencyClaim.type)
        if (declarations.isEmpty()) {
            return listOf()
        }
        val result = ArrayList<DeclarationWithIndex>()
        for (sourceDeclaration in declarations) {
            if (sourceDeclaration.declaration.isTemplate()) {
                continue
            }
            if (!dependencyClaim.tagMatches(sourceDeclaration.declaration.tag)) {
                continue
            }

            if (dependencyClaim.type.isAssignableFrom(sourceDeclaration.declaration.type) || ctx.serviceTypesHelper.isAssignableToUnwrapped(sourceDeclaration.declaration.type, dependencyClaim.type)) {
                result.add(sourceDeclaration)
            }
        }
        return result
    }

    fun toDependency(ctx: ProcessingContext, resolvedComponent: ResolvedComponent, dependencyClaim: DependencyClaim): SingleDependency {
        val isDirectAssignable = dependencyClaim.type.isAssignableFrom(resolvedComponent.type)
        val isWrappedAssignable = ctx.serviceTypesHelper.isAssignableToUnwrapped(resolvedComponent.type, dependencyClaim.type)
        check(isDirectAssignable || isWrappedAssignable)

        val targetDependency = if (isWrappedAssignable)
            WrappedTargetDependency(dependencyClaim, resolvedComponent)
        else
            TargetDependency(dependencyClaim, resolvedComponent)

        return when (dependencyClaim.claimType) {
            ONE_REQUIRED, NULLABLE_ONE -> targetDependency
            PROMISE_OF, NULLABLE_PROMISE_OF -> PromiseOfDependency(dependencyClaim, targetDependency)
            VALUE_OF, NULLABLE_VALUE_OF -> ValueOfDependency(dependencyClaim, targetDependency)
            ALL, ALL_OF_PROMISE, ALL_OF_VALUE, TYPE_REF -> throw java.lang.IllegalStateException()
        }
    }

    fun findDependenciesForAllOf(
        ctx: ProcessingContext,
        dependencyClaim: DependencyClaim,
        declarations: List<DeclarationWithIndex>,
        resolvedComponents: ResolvedComponents
    ): MutableList<SingleDependency> {
        val claimType = dependencyClaim.claimType
        val result = mutableListOf<SingleDependency>()
        components@ for (declarationWithIndex in declarations) {
            val declaration = declarationWithIndex.declaration
            if (!dependencyClaim.tagMatches(declaration.tag)) {
                continue@components
            }
            val component = resolvedComponents.getByDeclaration(declarationWithIndex)!!
            if (dependencyClaim.type.isAssignableFrom(declaration.type)) {
                val targetDependency = TargetDependency(dependencyClaim, component)
                val dependency = when (claimType) {
                    ALL -> targetDependency
                    ALL_OF_PROMISE -> PromiseOfDependency(dependencyClaim, targetDependency)
                    ALL_OF_VALUE -> ValueOfDependency(dependencyClaim, targetDependency)
                    else -> throw IllegalStateException("Unexpected value: " + dependencyClaim.claimType)
                }
                result.add(dependency)
            }
            if (ctx.serviceTypesHelper.isAssignableToUnwrapped(declaration.type, dependencyClaim.type)) {
                val targetDependency = WrappedTargetDependency(dependencyClaim, component)
                val dependency = when (claimType) {
                    ALL -> targetDependency
                    ALL_OF_PROMISE -> PromiseOfDependency(dependencyClaim, targetDependency)
                    ALL_OF_VALUE -> ValueOfDependency(dependencyClaim, targetDependency)
                    else -> throw IllegalStateException("Unexpected value: " + dependencyClaim.claimType)
                }
                result.add(dependency)
            }
        }
        return result
    }


    fun findDependencyDeclarationsFromTemplate(
        ctx: ProcessingContext,
        @Suppress("UNUSED_PARAMETER")
        forDeclaration: ComponentDeclaration,
        templateDeclarations: List<ComponentDeclaration>,
        dependencyClaim: DependencyClaim
    ): List<ComponentDeclaration> {
        val result = arrayListOf<ComponentDeclaration>()
        for (template in templateDeclarations) {
            if (!dependencyClaim.tagMatches(template.tag)) {
                continue
            }
            when (template) {
                is ComponentDeclaration.FromModuleComponent -> {
                    val match = ComponentTemplateHelper.match(ctx, template.method.typeParameters, template.type, dependencyClaim.type)
                    if (match !is ComponentTemplateHelper.TemplateMatch.Some) {
                        continue
                    }
                    val map = match.map
                    val realReturnType = ComponentTemplateHelper.replace(ctx.resolver, template.type, map)!!
                    if (!dependencyClaim.type.isAssignableFrom(realReturnType)) {
                        continue
                    }

                    val realParams = mutableListOf<KSType>()
                    for (methodParameterType in template.methodParameterTypes) {
                        realParams.add(ComponentTemplateHelper.replace(ctx.resolver, methodParameterType, map)!!)
                    }
                    val realTypeParameters = mutableListOf<KSTypeArgument>()
                    for (typeParameter in template.method.typeParameters) {
                        realTypeParameters.add(ComponentTemplateHelper.replace(ctx.resolver, typeParameter, map)!!)
                    }
                    result.add(
                        ComponentDeclaration.FromModuleComponent(
                            realReturnType,
                            template.module,
                            template.tag,
                            template.method,
                            realParams,
                            realTypeParameters,
                            template.isInterceptor
                        )
                    )
                }

                is ComponentDeclaration.AnnotatedComponent -> {
                    val match = ComponentTemplateHelper.match(ctx, template.classDeclaration.typeParameters, template.type, dependencyClaim.type)
                    if (match !is ComponentTemplateHelper.TemplateMatch.Some) {
                        continue
                    }
                    val map = match.map
                    val realReturnType = ComponentTemplateHelper.replace(ctx.resolver, template.type, map)!!
                    if (!dependencyClaim.type.isAssignableFrom(realReturnType)) {
                        continue
                    }

                    val realParams = mutableListOf<KSType>()
                    for (methodParameterType in template.methodParameterTypes) {
                        realParams.add(ComponentTemplateHelper.replace(ctx.resolver, methodParameterType, map)!!)
                    }
                    val realTypeParameters = mutableListOf<KSTypeArgument>()
                    for (typeParameter in template.classDeclaration.typeParameters) {
                        realTypeParameters.add(ComponentTemplateHelper.replace(ctx.resolver, typeParameter, map)!!)
                    }
                    result.add(
                        ComponentDeclaration.AnnotatedComponent(
                            realReturnType,
                            template.classDeclaration,
                            template.tag,
                            template.constructor,
                            realParams,
                            realTypeParameters,
                            template.isInterceptor
                        )
                    )
                }

                is ComponentDeclaration.PromisedProxyComponent -> {
                    val match = ComponentTemplateHelper.match(ctx, template.classDeclaration.typeParameters, template.type, dependencyClaim.type)
                    if (match !is ComponentTemplateHelper.TemplateMatch.Some) {
                        continue
                    }
                    val map = match.map
                    val realReturnType = ComponentTemplateHelper.replace(ctx.resolver, template.type, map)!!
                    if (!dependencyClaim.type.isAssignableFrom(realReturnType)) {
                        continue
                    }

                    result.add(template.copy(type = realReturnType))
                }

                is ComponentDeclaration.FromExtensionComponent -> {
                    val sourceMethod = template.source
                    if (sourceMethod !is KSFunctionDeclaration) {
                        continue
                    }

                    val classDeclaration = sourceMethod.returnType!!.resolve().declaration
                    val match = ComponentTemplateHelper.match(ctx, classDeclaration.typeParameters, template.type, dependencyClaim.type)
                    if (match !is ComponentTemplateHelper.TemplateMatch.Some) {
                        continue
                    }
                    val map = match.map
                    val realReturnType = ComponentTemplateHelper.replace(ctx.resolver, template.type, map)!!
                    if (!dependencyClaim.type.isAssignableFrom(realReturnType)) {
                        continue
                    }

                    val realParams = mutableListOf<KSType>()
                    for (methodParameterType in template.methodParameterTypes) {
                        realParams.add(ComponentTemplateHelper.replace(ctx.resolver, methodParameterType, map)!!)
                    }
                    result.add(
                        ComponentDeclaration.FromExtensionComponent(
                            realReturnType,
                            sourceMethod,
                            realParams,
                            template.methodParameterTags,
                            template.tag,
                            template.generator
                        )
                    )
                }

                is ComponentDeclaration.OptionalComponent -> throw IllegalStateException()
            }
        }
        if (result.isEmpty()) {
            return result
        }
        if (result.size == 1) {
            return result
        }
        val exactMatch = result.filter { it.type == dependencyClaim.type }
        if (exactMatch.isNotEmpty()) {
            return exactMatch
        }
        val nonDefault = result.filter { !it.isDefault() }
        if (nonDefault.isNotEmpty()) {
            return nonDefault
        }
        return result
    }

    fun findInterceptorDeclarations(ctx: ProcessingContext, sourceDeclarations: ComponentDeclarations, type: KSType): List<DeclarationWithIndex> {
        val result = mutableListOf<DeclarationWithIndex>()
        for (sourceDeclaration in sourceDeclarations.interceptors()) {
            if (sourceDeclaration.declaration.isInterceptor && ctx.serviceTypesHelper.isInterceptorFor(sourceDeclaration.declaration.type, type)) {
                result.add(sourceDeclaration)
            }
        }
        return result
    }
}
