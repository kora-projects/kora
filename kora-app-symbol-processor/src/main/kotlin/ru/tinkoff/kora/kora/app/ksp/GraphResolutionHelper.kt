package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependency.*
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.TagUtils
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

object GraphResolutionHelper {
    fun findDependency(ctx: ProcessingContext, forDeclaration: ComponentDeclaration, resolvedComponents: List<ResolvedComponent>, dependencyClaim: DependencyClaim): SingleDependency? {
        val dependencies = findDependencies(ctx, resolvedComponents, dependencyClaim)
        if (dependencies.size == 1) {
            return dependencies[0]
        }
        if (dependencies.isEmpty()) {
            return null
        }
        val deps = dependencies.joinToString("\n") { it.toString() }.prependIndent("  ")
        throw ProcessingErrorException(
            "More than one component matches dependency claim ${dependencyClaim.type.declaration.qualifiedName?.asString()} tag=${dependencyClaim.tags}:\n$deps",
            forDeclaration.source
        )
    }

    fun findDependencies(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>, dependencyClaim: DependencyClaim): List<SingleDependency> {
        val result = ArrayList<SingleDependency>(4)
        for (resolvedComponent in resolvedComponents) {
            if (!dependencyClaim.tagsMatches(resolvedComponent.tags)) {
                continue
            }
            val isDirectAssignable = dependencyClaim.type.isAssignableFrom(resolvedComponent.type)
            val isWrappedAssignable = ctx.serviceTypesHelper.isAssignableToUnwrapped(resolvedComponent.type, dependencyClaim.type)
            if (!isDirectAssignable && !isWrappedAssignable) {
                continue
            }
            val targetDependency = if (isWrappedAssignable) WrappedTargetDependency(dependencyClaim, resolvedComponent) else TargetDependency(dependencyClaim, resolvedComponent)
            when (dependencyClaim.claimType) {
                DependencyClaim.DependencyClaimType.ONE_REQUIRED -> result.add(targetDependency)
                DependencyClaim.DependencyClaimType.NULLABLE_ONE -> result.add(targetDependency)
                DependencyClaim.DependencyClaimType.VALUE_OF -> result.add(ValueOfDependency(dependencyClaim, targetDependency))
                DependencyClaim.DependencyClaimType.NULLABLE_VALUE_OF -> result.add(ValueOfDependency(dependencyClaim, targetDependency))
                DependencyClaim.DependencyClaimType.PROMISE_OF -> result.add(PromiseOfDependency(dependencyClaim, targetDependency))
                DependencyClaim.DependencyClaimType.NULLABLE_PROMISE_OF -> result.add(PromiseOfDependency(dependencyClaim, targetDependency))
                else -> throw IllegalStateException()
            }
        }
        return result
    }

    fun findFinalDependency(ctx: ProcessingContext, dependencyClaim: DependencyClaim): ComponentDeclaration? {
        val declaration = dependencyClaim.type.declaration
        if (declaration !is KSClassDeclaration) {
            return null
        }
        if (declaration.isOpen()) {
            return null
        }
        if (declaration.packageName.asString() == "kotlin") {
            return null
        }
        if (declaration.typeParameters.isNotEmpty()) {
            return null
        }
        if (declaration.primaryConstructor == null) {
            return null
        }
        val tags = TagUtils.parseTagValue(declaration)
        if (dependencyClaim.tagsMatches(tags)) {
            return ComponentDeclaration.fromDependency(ctx, declaration)
        }
        return null
    }

    fun findDependenciesForAllOf(ctx: ProcessingContext, dependencyClaim: DependencyClaim, resolvedComponents: List<ResolvedComponent>): List<SingleDependency> {
        val result = mutableListOf<SingleDependency>()
        for (component in resolvedComponents) {
            if (!dependencyClaim.tagsMatches(component.tags)) {
                continue
            }
            if (dependencyClaim.type.isAssignableFrom(component.type)) {
                val targetDependency = TargetDependency(dependencyClaim, component)
                val dependency = when (dependencyClaim.claimType) {
                    DependencyClaim.DependencyClaimType.ALL -> targetDependency
                    DependencyClaim.DependencyClaimType.ALL_OF_VALUE -> ValueOfDependency(dependencyClaim, targetDependency)
                    DependencyClaim.DependencyClaimType.ALL_OF_PROMISE -> PromiseOfDependency(dependencyClaim, targetDependency)
                    else -> throw IllegalStateException()
                }
                result.add(dependency)
            }
            if (ctx.serviceTypesHelper.isAssignableToUnwrapped(component.type, dependencyClaim.type)) {
                val targetDependency = WrappedTargetDependency(dependencyClaim, component)
                val dependency = when (dependencyClaim.claimType) {
                    DependencyClaim.DependencyClaimType.ALL -> targetDependency
                    DependencyClaim.DependencyClaimType.ALL_OF_VALUE -> ValueOfDependency(dependencyClaim, targetDependency)
                    DependencyClaim.DependencyClaimType.ALL_OF_PROMISE -> PromiseOfDependency(dependencyClaim, targetDependency)
                    else -> throw IllegalStateException()
                }
                result.add(dependency)
            }
        }
        return result
    }


    fun findDependencyDeclarationFromTemplate(
        ctx: ProcessingContext,
        forDeclaration: ComponentDeclaration,
        templateDeclarations: List<ComponentDeclaration>,
        dependencyClaim: DependencyClaim
    ): ComponentDeclaration? {
        val result = findDependencyDeclarationsFromTemplate(ctx, forDeclaration, templateDeclarations, dependencyClaim)
        if (result.isEmpty()) {
            return null
        }
        // todo exact match
        if (result.size == 1) {
            return result[0]
        }
        val deps = result.asSequence().map { it.toString() }.joinToString("\n").prependIndent("  ")
        throw ProcessingErrorException(
            "More than one component matches dependency claim ${dependencyClaim.type.declaration.qualifiedName?.asString()} tag=${dependencyClaim.tags}:\n$deps",
            forDeclaration.source
        )
    }

    fun findDependencyDeclarationsFromTemplate(
        ctx: ProcessingContext,
        forDeclaration: ComponentDeclaration,
        templateDeclarations: List<ComponentDeclaration>,
        dependencyClaim: DependencyClaim
    ): List<ComponentDeclaration> {
        val result = arrayListOf<ComponentDeclaration>()
        for (template in templateDeclarations) {
            if (!dependencyClaim.tagsMatches(template.tags)) {
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
                            template.tags,
                            template.method,
                            realParams,
                            realTypeParameters
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
                            template.tags,
                            template.constructor,
                            realParams,
                            realTypeParameters
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


                    result.add(template.copy(type = realReturnType))
                }

                is ComponentDeclaration.FromExtensionComponent -> {
                    val classDeclaration = template.sourceMethod.returnType!!.resolve().declaration
                    val match = ComponentTemplateHelper.match(ctx, classDeclaration.typeParameters, template.type, dependencyClaim.type)
                    if (match !is ComponentTemplateHelper.TemplateMatch.Some) {
                        continue
                    }
                    val map = match.map
                    val realReturnType = ComponentTemplateHelper.replace(ctx.resolver, template.type, map)!!

                    val realParams = mutableListOf<KSType>()
                    for (methodParameterType in template.methodParameterTypes) {
                        realParams.add(ComponentTemplateHelper.replace(ctx.resolver, methodParameterType, map)!!)
                    }
                    result.add(
                        ComponentDeclaration.FromExtensionComponent(
                            realReturnType,
                            template.sourceMethod,
                            realParams
                        )
                    )
                }

                is ComponentDeclaration.DiscoveredAsDependencyComponent -> throw IllegalStateException()
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


    fun findDependencyDeclaration(
        ctx: ProcessingContext,
        forDeclaration: ComponentDeclaration,
        sourceDeclarations: List<ComponentDeclaration>,
        dependencyClaim: DependencyClaim
    ): ComponentDeclaration? {
        val claimType = dependencyClaim.claimType
        assert(claimType !in listOf(DependencyClaim.DependencyClaimType.ALL, DependencyClaim.DependencyClaimType.ALL_OF_PROMISE, DependencyClaim.DependencyClaimType.ALL_OF_VALUE))
        val declarations = findDependencyDeclarations(ctx, sourceDeclarations, dependencyClaim)
        if (declarations.size == 1) {
            return declarations[0]
        }
        if (declarations.isEmpty()) {
            return null
        }
        val nonDefaultComponents = declarations.filter { !it.isDefault() }
        if (nonDefaultComponents.size == 1) {
            return nonDefaultComponents[0]
        }

        val exactMatch = declarations.asSequence()
            .filter { it.type == dependencyClaim.type || ctx.serviceTypesHelper.isSameToUnwrapped(it.type, dependencyClaim.type) }
            .toList()
        if (exactMatch.size == 1) {
            return exactMatch[0]
        }

        val deps = declarations.asSequence().map { it.toString() }.joinToString("\n").prependIndent("  ")
        throw ProcessingErrorException(
            "More than one component matches dependency claim ${dependencyClaim.type.declaration.qualifiedName?.asString()} tag=${dependencyClaim.tags}:\n$deps",
            forDeclaration.source
        )
    }

    fun findDependencyDeclarations(ctx: ProcessingContext, sourceDeclarations: List<ComponentDeclaration>, dependencyClaim: DependencyClaim): List<ComponentDeclaration> {
        val result = mutableListOf<ComponentDeclaration>()
        for (sourceDeclaration in sourceDeclarations) {
            if (!dependencyClaim.tagsMatches(sourceDeclaration.tags)) {
                continue
            }
            if (dependencyClaim.type.isAssignableFrom(sourceDeclaration.type) || ctx.serviceTypesHelper.isAssignableToUnwrapped(sourceDeclaration.type, dependencyClaim.type)) {
                result.add(sourceDeclaration)
            }
        }
        return result
    }

    fun findInterceptorDeclarations(ctx: ProcessingContext, sourceDeclarations: List<ComponentDeclaration>, type: KSType): MutableList<ComponentDeclaration> {
        val result = mutableListOf<ComponentDeclaration>()
        for (sourceDeclaration in sourceDeclarations) {
            if (ctx.serviceTypesHelper.isInterceptorFor(sourceDeclaration.type, type)) {
                result.add(sourceDeclaration)
            }
        }
        return result
    }
}
