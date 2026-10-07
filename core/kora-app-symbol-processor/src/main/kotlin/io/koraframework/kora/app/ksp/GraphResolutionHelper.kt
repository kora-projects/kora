package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.Origin
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.component.ComponentDependency
import io.koraframework.kora.app.ksp.component.ComponentDependency.*
import io.koraframework.kora.app.ksp.component.DependencyClaim
import io.koraframework.kora.app.ksp.component.DependencyClaim.DependencyClaimType.*
import io.koraframework.kora.app.ksp.component.ResolvedComponent
import io.koraframework.kora.app.ksp.component.ResolvedComponents
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.kora.app.ksp.declaration.ComponentDeclarations
import io.koraframework.kora.app.ksp.declaration.DeclarationWithIndex


object GraphResolutionHelper {
    /**
     * A Java component argument is a platform type: `List<String!>` is both `List<String>` and `List<String?>` for Kotlin,
     * so the nullability of the claim arguments is ignored for components declared in Java, as Java annotation processor does.
     */
    private fun isAssignable(ctx: ProcessingContext, claim: DependencyClaim, declaration: ComponentDeclaration): Boolean {
        if (claim.type.isAssignableFrom(declaration.type)) {
            return true
        }
        // only plain and nullable claims: their generated code casts the component to the claimed type (see TargetDependency.write)
        if (claim.claimType != ONE_REQUIRED && claim.claimType != NULLABLE_ONE) {
            return false
        }
        val origin = declaration.source.origin
        return (origin == Origin.JAVA || origin == Origin.JAVA_LIB) && claim.type.withNotNullArguments(ctx).isAssignableFrom(declaration.type)
    }

    private fun KSType.withNotNullArguments(ctx: ProcessingContext): KSType {
        if (arguments.isEmpty()) {
            return this
        }
        return replace(arguments.map { arg ->
            val argType = arg.type?.resolve() ?: return@map arg
            ctx.resolver.getTypeArgument(ctx.resolver.createKSTypeReferenceFromKSType(argType.makeNotNullable().withNotNullArguments(ctx)), arg.variance)
        })
    }

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

            if (isAssignable(ctx, dependencyClaim, sourceDeclaration.declaration) || ctx.serviceTypesHelper.isAssignableToUnwrapped(sourceDeclaration.declaration.type, dependencyClaim.type)) {
                result.add(sourceDeclaration)
            }
        }
        return result
    }

    fun findSameTypeDeclarationsWithDifferentTags(
        ctx: ProcessingContext,
        componentDeclarations: ComponentDeclarations,
        dependencyClaim: DependencyClaim
    ): List<ComponentDeclaration> {
        val declarations = componentDeclarations.getByType(dependencyClaim.type)
        if (declarations.isEmpty()) {
            return listOf()
        }
        val result = ArrayList<ComponentDeclaration>()
        for (sourceDeclaration in declarations) {
            val declaration = sourceDeclaration.declaration
            if (declaration.isTemplate()) {
                continue
            }
            if (dependencyClaim.tagMatches(declaration.tag)) {
                continue
            }

            if (isAssignable(ctx, dependencyClaim, declaration) || ctx.serviceTypesHelper.isAssignableToUnwrapped(declaration.type, dependencyClaim.type)) {
                result.add(declaration)
            }
        }
        return result
    }

    /**
     * Components that would match the claim if not for nullability, e.g. component of type `T?` for dependency `T`
     */
    fun findSameTypeDeclarationsWithDifferentNullability(
        componentDeclarations: ComponentDeclarations,
        dependencyClaim: DependencyClaim
    ): List<ComponentDeclaration> {
        val nullableClaimType = dependencyClaim.type.makeNullable()
        // declarations of nullable non-generic types are indexed by nullable type name, which getByType(KSType) never looks up
        val nullableTypeName = dependencyClaim.type.toTypeName().copy(nullable = true)
        val nullableDeclarations = if (nullableTypeName is ClassName) componentDeclarations.getByType(nullableTypeName) else listOf()
        return (componentDeclarations.getByType(dependencyClaim.type) + nullableDeclarations)
            .map { it.declaration }
            .distinct()
            .filter { !it.isTemplate() && dependencyClaim.tagMatches(it.tag) }
            .filter { !dependencyClaim.type.isAssignableFrom(it.type) && nullableClaimType.isAssignableFrom(it.type) }
    }

    fun toDependency(ctx: ProcessingContext, resolvedComponent: ResolvedComponent, dependencyClaim: DependencyClaim): SingleDependency {
        // resolvedComponent.type may differ from declaration.type, so both checks are needed
        val isDirectAssignable = dependencyClaim.type.isAssignableFrom(resolvedComponent.type) || isAssignable(ctx, dependencyClaim, resolvedComponent.declaration)
        val isWrappedAssignable = ctx.serviceTypesHelper.isAssignableToUnwrapped(resolvedComponent.type, dependencyClaim.type)
        check(isDirectAssignable || isWrappedAssignable) {
            "Kora internal error: resolved component is not assignable to dependency claim. Component=${resolvedComponent.declaration.declarationString()}, claim=$dependencyClaim"
        }

        val targetDependency = if (isWrappedAssignable)
            WrappedTargetDependency(dependencyClaim, resolvedComponent)
        else
            TargetDependency(dependencyClaim, resolvedComponent)

        return when (dependencyClaim.claimType) {
            ONE_REQUIRED, NULLABLE_ONE, NODE_OF -> targetDependency
            PROMISE_OF, NULLABLE_PROMISE_OF -> PromiseOfDependency(dependencyClaim, targetDependency)
            VALUE_OF, NULLABLE_VALUE_OF -> ValueOfDependency(dependencyClaim, targetDependency)
            ALL, ALL_OF_PROMISE, ALL_OF_VALUE, TYPE_REF, GRAPH -> throw IllegalStateException("Kora internal error: unsupported single dependency claim type ${dependencyClaim.claimType} for $dependencyClaim")
        }
    }

    fun toOneOfDependency(ctx: ProcessingContext, resolvedComponents: List<ResolvedComponent>, dependencyClaim: DependencyClaim): ComponentDependency {
        val dependencies = mutableListOf<SingleDependency>()

        for (resolvedComponent in resolvedComponents) {
            dependencies.add(toDependency(ctx, resolvedComponent, dependencyClaim))
        }

        return OneOfDependency(dependencyClaim, dependencies)
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
                            template.isInterceptor,
                            template.condition
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
                            template.isInterceptor,
                            template.condition
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

                is ComponentDeclaration.OptionalComponent -> throw IllegalStateException("Kora internal error: optional synthetic component cannot be used as a component template: $template")
            }
        }
        if (result.isEmpty()) {
            return result
        }
        if (result.size == 1) {
            return result
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


    fun findDependenciesForAllOf(
        ctx: ProcessingContext,
        dependencyClaim: DependencyClaim,
        declarations: List<DeclarationWithIndex>,
        resolvedComponents: ResolvedComponents
    ): List<SingleDependency> {
        val claimType = dependencyClaim.claimType
        val result = mutableListOf<SingleDependency>()
        for (declarationWithIndex in declarations) {
            val declaration = declarationWithIndex.declaration
            if (!dependencyClaim.tagMatches(declaration.tag)) {
                continue
            }
            val component = resolvedComponents.getByDeclaration(declarationWithIndex)
            if (component == null) {
                if (declaration.isDefault()) {
                    // that's fine, default component wasn't directly requested by anyone, so we don't need it
                    continue
                } else {
                    throw IllegalStateException("Kora internal error: non-default All<T> dependency declaration is not resolved: ${declaration.declarationString()}")
                }
            }

            if (dependencyClaim.type.isAssignableFrom(declaration.type)) {
                val targetDependency = TargetDependency(dependencyClaim, component)
                val dependency = when (claimType) {
                    ALL -> targetDependency
                    ALL_OF_PROMISE -> PromiseOfDependency(dependencyClaim, targetDependency)
                    ALL_OF_VALUE -> ValueOfDependency(dependencyClaim, targetDependency)
                    else -> throw IllegalStateException("Kora internal error: unsupported All<T> claim type ${dependencyClaim.claimType} for $dependencyClaim")
                }
                result.add(dependency)
            }

            if (ctx.serviceTypesHelper.isAssignableToUnwrapped(declaration.type, dependencyClaim.type)) {
                val targetDependency = WrappedTargetDependency(dependencyClaim, component)
                val dependency = when (claimType) {
                    ALL -> targetDependency
                    ALL_OF_PROMISE -> PromiseOfDependency(dependencyClaim, targetDependency)
                    ALL_OF_VALUE -> ValueOfDependency(dependencyClaim, targetDependency)
                    else -> throw IllegalStateException("Kora internal error: unsupported wrapped All<T> claim type ${dependencyClaim.claimType} for $dependencyClaim")
                }
                result.add(dependency)
            }
        }
        return result
    }

}
