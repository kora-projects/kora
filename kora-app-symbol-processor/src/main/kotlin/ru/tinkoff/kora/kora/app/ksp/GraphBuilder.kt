package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependency
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependencyHelper
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim.DependencyClaimType.*
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.kora.app.ksp.exception.CircularDependencyException
import ru.tinkoff.kora.kora.app.ksp.exception.DuplicateDependencyException
import ru.tinkoff.kora.kora.app.ksp.exception.UnresolvedDependencyException
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import java.util.*

class GraphBuilder {
    val ctx: ProcessingContext
    val root: KSClassDeclaration
    val allModules: List<KSClassDeclaration>
    val sourceDeclarations: MutableList<ComponentDeclaration>
    val templateDeclarations: MutableList<ComponentDeclaration>
    val rootSet: List<ComponentDeclaration>
    val resolvedComponents: MutableList<ResolvedComponent>
    val stack: Deque<ResolutionFrame>

    private constructor(
        ctx: ProcessingContext,
        root: KSClassDeclaration,
        allModules: List<KSClassDeclaration>,
        sourceDeclarations: MutableList<ComponentDeclaration>,
        templateDeclarations: MutableList<ComponentDeclaration>,
        rootSet: List<ComponentDeclaration>,
        resolvedComponents: MutableList<ResolvedComponent>,
        stack: Deque<ResolutionFrame>
    ) {
        this.ctx = ctx
        this.allModules = allModules
        this.sourceDeclarations = sourceDeclarations
        this.templateDeclarations = templateDeclarations
        this.resolvedComponents = resolvedComponents
        this.stack = stack
        this.root = root
        this.rootSet = rootSet
    }

    constructor(from: GraphBuilder) {
        this.ctx = from.ctx
        this.root = from.root
        this.rootSet = from.rootSet
        this.allModules = from.allModules
        this.sourceDeclarations = ArrayList(from.sourceDeclarations)
        this.templateDeclarations = ArrayList(from.templateDeclarations)
        this.resolvedComponents = ArrayList(from.resolvedComponents)
        this.stack = ArrayDeque(from.stack)
    }

    constructor(
        ctx: ProcessingContext,
        root: KSClassDeclaration,
        allModules: List<KSClassDeclaration>,
        sourceDeclarations: MutableList<ComponentDeclaration>,
        templateDeclarations: MutableList<ComponentDeclaration>,
    ) {
        this.ctx = ctx
        this.allModules = allModules
        this.sourceDeclarations = sourceDeclarations
        this.templateDeclarations = templateDeclarations
        this.root = root
        this.rootSet = sourceDeclarations.filter {
            it.source.isAnnotationPresent(CommonClassNames.root) || it is ComponentDeclaration.AnnotatedComponent && it.classDeclaration.isAnnotationPresent(CommonClassNames.root)
        }
        this.resolvedComponents = ArrayList()
        this.stack = ArrayDeque()
        for (i in rootSet.indices) {
            stack.push(ResolutionFrame.Root(i, rootSet[i]))
        }

    }


    sealed interface ResolutionFrame {
        data class Root(val rootIndex: Int, val declaration: ComponentDeclaration) : ResolutionFrame
        data class Component(
            val declaration: ComponentDeclaration,
            val dependenciesToFind: List<DependencyClaim> = ComponentDependencyHelper.parseDependencyClaim(declaration),
            val resolvedDependencies: MutableList<ComponentDependency> = ArrayList(dependenciesToFind.size),
            val currentDependency: Int = 0
        ) : ResolutionFrame
    }

    fun findResolvedComponent(declaration: ComponentDeclaration) = resolvedComponents.asSequence().filter { it.declaration === declaration }.firstOrNull()

    fun build(): ResolvedGraph {
        if (rootSet.isEmpty()) {
            throw ProcessingErrorException(
                "@KoraApp has no root components, expected at least one component annotated with @Root",
                root
            )
        }
        frame@ while (stack.isNotEmpty()) {
            val frame = stack.removeLast()
            if (frame is ResolutionFrame.Root) {
                val declaration = rootSet[frame.rootIndex]
                if (findResolvedComponent(declaration) != null) {
                    continue
                }
                stack.addLast(ResolutionFrame.Component(declaration))
                stack.addAll(findInterceptors(declaration))
                continue
            }
            frame as ResolutionFrame.Component
            val declaration = frame.declaration
            val dependenciesToFind = frame.dependenciesToFind
            val resolvedDependencies = frame.resolvedDependencies
            if (checkCycle(declaration)) {
                continue
            }

            dependency@ for (currentDependency in frame.currentDependency until dependenciesToFind.size) {
                val dependencyClaim = dependenciesToFind[currentDependency]
                ctx.kspLogger.info("Resolving ${dependencyClaim.type} for ${declaration.source}")
                if (dependencyClaim.claimType in listOf(ALL, ALL_OF_PROMISE, ALL_OF_VALUE)) {
                    val allOfDependency = processAllOf(frame, currentDependency)
                    if (allOfDependency == null) {
                        continue@frame
                    } else {
                        resolvedDependencies.add(allOfDependency)
                        continue@dependency
                    }
                }
                if (dependencyClaim.claimType == TYPE_REF) {
                    resolvedDependencies.add(ComponentDependency.TypeOfDependency(dependencyClaim))
                    continue@dependency
                }
                val dependencyComponent = GraphResolutionHelper.findDependency(ctx, declaration, resolvedComponents, dependencyClaim)
                if (dependencyComponent != null) {
                    resolvedDependencies.add(dependencyComponent)
                    continue@dependency
                }
                val dependencyDeclaration = GraphResolutionHelper.findDependencyDeclaration(ctx, declaration, sourceDeclarations, dependencyClaim)
                if (dependencyDeclaration != null) {
                    stack.addLast(frame.copy(currentDependency = currentDependency))
                    stack.addLast(ResolutionFrame.Component(dependencyDeclaration))
                    stack.addAll(findInterceptors(dependencyDeclaration))
                    continue@frame
                }
                val templates = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(ctx, declaration, templateDeclarations, dependencyClaim)
                if (templates.isNotEmpty()) {
                    if (templates.size == 1) {
                        val template = templates[0]
                        sourceDeclarations.add(template)
                        stack.addLast(frame.copy(currentDependency = currentDependency))
                        stack.addLast(ResolutionFrame.Component(template))
                        stack.addAll(findInterceptors(template))
                        continue@frame
                    }
                    val results = ArrayList<ResolvedGraph>(templates.size)
                    var exception: UnresolvedDependencyException? = null
                    for (template in templates) {
                        val fork = GraphBuilder(this)
                        fork.sourceDeclarations.add(template)
                        fork.stack.addLast(frame.copy(currentDependency = currentDependency))
                        fork.stack.addLast(ResolutionFrame.Component(template))
                        fork.stack.addAll(fork.findInterceptors(template))
                        try {
                            results.add(fork.build())
                        } catch (e: UnresolvedDependencyException) {
                            if (exception != null) {
                                exception.addSuppressed(e)
                            } else {
                                exception = e
                            }
                        }
                    }
                    if (results.size == 1) {
                        return results.first()
                    }
                    if (results.size > 1) {
                        throw DuplicateDependencyException(dependencyClaim, declaration, templates)
                    }
                    throw exception!!
                }
                val optionalDependency = findOptionalDependency(dependencyClaim)
                if (optionalDependency != null) {
                    resolvedDependencies.add(optionalDependency)
                    continue@dependency
                }
                if (dependencyClaim.type.declaration.qualifiedName!!.asString() == "java.util.Optional") {
                    // todo just add predefined template
                    val optionalDeclaration = ComponentDeclaration.OptionalComponent(dependencyClaim.type, dependencyClaim.tags)
                    sourceDeclarations.add(optionalDeclaration)
                    stack.addLast(frame.copy(currentDependency = currentDependency))
                    val type = dependencyClaim.type.arguments[0].type!!.resolve().makeNullable()
                    val claim = ComponentDependencyHelper.parseClaim(type, dependencyClaim.tags, declaration.source)
                    stack.addLast(
                        ResolutionFrame.Component(
                            optionalDeclaration, listOf(
                                claim
                            )
                        )
                    )
                    continue@frame
                }
                val finalClassComponent = GraphResolutionHelper.findFinalDependency(ctx, dependencyClaim)
                if (finalClassComponent != null) {
                    sourceDeclarations.add(finalClassComponent)
                    stack.addLast(frame.copy(currentDependency = currentDependency))
                    stack.addLast(ResolutionFrame.Component(finalClassComponent))
                    stack.addAll(findInterceptors(finalClassComponent))
                    continue@frame
                }
                val extension = ctx.extensions.findExtension(ctx.resolver, dependencyClaim.type, dependencyClaim.tags)
                if (extension != null) {
                    val extensionResult = extension()
                    if (extensionResult is ExtensionResult.CodeBlockResult) {
                        val extensionComponent = ComponentDeclaration.fromExtension(extensionResult)
                        if (extensionComponent.isTemplate()) {
                            templateDeclarations.add(extensionComponent)
                        } else {
                            sourceDeclarations.add(extensionComponent)
                        }
                        stack.addLast(frame.copy(currentDependency = currentDependency))
                        continue@frame
                    } else {
                        extensionResult as ExtensionResult.GeneratedResult
                        val extensionComponent = ComponentDeclaration.fromExtension(ctx, extensionResult)
                        if (extensionComponent.isTemplate()) {
                            templateDeclarations.add(extensionComponent)
                        } else {
                            sourceDeclarations.add(extensionComponent)
                        }
                        stack.addLast(frame.copy(currentDependency = currentDependency))
                        continue@frame
                    }
                }
                val hints = ctx.dependencyHintProvider.findHints(dependencyClaim.type, dependencyClaim.tags)
                throw UnresolvedDependencyException(stack, declaration, dependencyClaim, hints)
            }
            resolvedComponents.add(
                ResolvedComponent(
                    resolvedComponents.size,
                    declaration,
                    declaration.type,
                    declaration.tags,
                    listOf(),
                    resolvedDependencies
                )
            )
        }
        return ResolvedGraph(root, allModules, ArrayList(resolvedComponents))
    }

    private fun findOptionalDependency(dependencyClaim: DependencyClaim): ComponentDependency? {
        if (dependencyClaim.claimType == NULLABLE_ONE) {
            return ComponentDependency.NullDependency(dependencyClaim)
        }
        if (dependencyClaim.claimType == NULLABLE_VALUE_OF) {
            return ComponentDependency.NullDependency(dependencyClaim)
        }
        if (dependencyClaim.claimType == NULLABLE_PROMISE_OF) {
            return ComponentDependency.NullDependency(dependencyClaim)
        }
        return null
    }

    private fun processAllOf(componentFrame: ResolutionFrame.Component, currentDependency: Int): ComponentDependency? {
        val dependencyClaim = componentFrame.dependenciesToFind[currentDependency]
        val dependencies = GraphResolutionHelper.findDependencyDeclarations(ctx, sourceDeclarations, dependencyClaim)
        for (dependency in dependencies) {
            if (dependency.isDefault()) {
                continue
            }
            val resolved = findResolvedComponent(dependency)
            if (resolved != null) {
                continue
            }
            stack.addLast(componentFrame.copy(currentDependency = currentDependency))
            stack.addLast(ResolutionFrame.Component(dependency))
            stack.addAll(findInterceptors(dependency))
            return null
        }
        if (dependencyClaim.claimType == ALL || dependencyClaim.claimType == ALL_OF_VALUE || dependencyClaim.claimType == ALL_OF_PROMISE) {
            return ComponentDependency.AllOfDependency(dependencyClaim)
        }
        throw IllegalStateException()
    }

    private fun findInterceptors(declaration: ComponentDeclaration): Sequence<ResolutionFrame.Component> {
        return GraphResolutionHelper.findInterceptorDeclarations(ctx, sourceDeclarations, declaration.type)
            .asSequence()
            .filter { id -> resolvedComponents.none { it.declaration === id } && stack.none { it is ResolutionFrame.Component && it.declaration == id } }
            .map { ResolutionFrame.Component(it) }

    }

    private fun generatePromisedProxy(ctx: ProcessingContext, claimTypeDeclaration: KSClassDeclaration): ComponentDeclaration {
        val resultClassName = claimTypeDeclaration.getOuterClassesAsPrefix() + claimTypeDeclaration.simpleName.asString() + "_PromisedProxy"
        val packageName = claimTypeDeclaration.packageName.asString()
        val alreadyGenerated = ctx.resolver.getClassDeclarationByName("$packageName.$resultClassName")
        if (alreadyGenerated != null) {
            return ComponentDeclaration.PromisedProxyComponent(
                claimTypeDeclaration.asType(listOf()), // some weird behaviour here: asType with empty list returns type with type parameters as type, no other way to get them
                claimTypeDeclaration,
                ClassName(packageName, resultClassName)
            )
        }

        val typeTpr = claimTypeDeclaration.typeParameters.toTypeParameterResolver()
        val typeParameters = claimTypeDeclaration.typeParameters.map { it.toTypeVariableName(typeTpr) }
        val typeName = if (typeParameters.isEmpty()) claimTypeDeclaration.toClassName() else claimTypeDeclaration.toClassName().parameterizedBy(typeParameters)
        val promiseType = CommonClassNames.promiseOf.parameterizedBy(WildcardTypeName.producerOf(typeName))
        val type = TypeSpec.classBuilder(resultClassName)
            .generated(GraphBuilder::class)
            .addProperty("promise", promiseType, KModifier.PRIVATE, KModifier.FINAL)
            .addProperty(PropertySpec.builder("delegate", typeName.copy(true), KModifier.PRIVATE).mutable(true).initializer("null").build())
            .addSuperinterface(CommonClassNames.promisedProxy.parameterizedBy(typeName))
            .addSuperinterface(CommonClassNames.refreshListener)
            .addFunction(
                FunSpec.constructorBuilder()
                    .addParameter("promise", promiseType)
                    .addStatement("this.promise = promise")
                    .build()
            )
            .addFunction(
                FunSpec.builder("graphRefreshed")
                    .addModifiers(KModifier.OVERRIDE)
                    .addStatement("this.delegate = null")
                    .addStatement("this.getDelegate()")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getDelegate")
                    .addModifiers(KModifier.PRIVATE)
                    .returns(typeName)
                    .addCode(
                        CodeBlock.builder()
                            .addStatement("var delegate = this.delegate")
                            .controlFlow("if (delegate == null)") {
                                addStatement("delegate = this.promise.get().get()!!")
                                addStatement("this.delegate = delegate")
                            }
                            .addStatement("return delegate")
                            .build()
                    )
                    .build()
            )
        for (typeParameter in claimTypeDeclaration.typeParameters) {
            type.addTypeVariable(typeParameter.toTypeVariableName(typeTpr))
        }
        if (claimTypeDeclaration.classKind == ClassKind.INTERFACE) {
            type.addSuperinterface(typeName)
        } else {
            type.superclass(typeName)
        }

        for (fn in claimTypeDeclaration.getAllFunctions()) {
            if (!fn.isOpen() || fn.modifiers.contains(Modifier.PRIVATE)) {
                continue
            }
            if (fn.simpleName.asString() in setOf("equals", "hashCode", "toString")) {
                continue // todo figure out a better way to handle this
            }
            val funTpr = fn.typeParameters.toTypeParameterResolver(typeTpr)
            val method = FunSpec.builder(fn.simpleName.getShortName())
                .addModifiers(KModifier.OVERRIDE)
                .returns(fn.returnType!!.resolve().toTypeName(funTpr))
            if (fn.isSuspend()) {
                method.addModifiers(KModifier.SUSPEND)
            }
            method.addCode("return this.getDelegate().%L(", fn.simpleName.getShortName())
            for ((i, param) in fn.parameters.withIndex()) {
                if (i > 0) {
                    method.addCode(", ")
                }
                method.addCode("%N", param.name!!.getShortName())
                method.addParameter(param.name!!.getShortName(), param.type.toTypeName(funTpr))
            }
            method.addCode(")\n")
            type.addFunction(method.build())
        }
        for (allProperty in claimTypeDeclaration.getAllProperties()) {
            val prop = PropertySpec.builder(allProperty.simpleName.asString(), allProperty.type.resolve().toTypeName(), KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return this.getDelegate().%N", allProperty.simpleName.getShortName())
                        .build()
                )
                .build()
            type.addProperty(prop)
        }

        val file = FileSpec.builder(packageName, resultClassName)
            .addType(type.build())
            .build()
        file.writeTo(ctx.codeGenerator, true)

        return ComponentDeclaration.PromisedProxyComponent(
            claimTypeDeclaration.asType(listOf()), // some weird behaviour here: asType with empty list returns type with type parameters as type, no other way to get them
            claimTypeDeclaration,
            ClassName(packageName, resultClassName)
        )
    }

    private fun checkCycle(declaration: ComponentDeclaration): Boolean {
        val prevFrame = stack.peekLast()
        if (prevFrame !is ResolutionFrame.Component) {
            return false
        }
        if (prevFrame.dependenciesToFind.isEmpty()) {
            return false
        }
        val dependencyClaim = prevFrame.dependenciesToFind[prevFrame.currentDependency]
        val claimTypeDeclaration = dependencyClaim.type.declaration
        for (frame in stack) {
            if (frame !is ResolutionFrame.Component || frame.declaration !== declaration) {
                continue
            }
            val circularDependencyException = CircularDependencyException(listOf(prevFrame.declaration, declaration), frame.declaration)
            if (claimTypeDeclaration !is KSClassDeclaration) throw circularDependencyException
            if (claimTypeDeclaration.classKind != ClassKind.INTERFACE && !(claimTypeDeclaration.classKind == ClassKind.CLASS && claimTypeDeclaration.isOpen())) throw circularDependencyException
            val proxyDependencyClaim = DependencyClaim(
                dependencyClaim.type, setOf(CommonClassNames.promisedProxy.canonicalName), dependencyClaim.claimType
            )
            val alreadyGenerated = GraphResolutionHelper.findDependency(ctx, prevFrame.declaration, resolvedComponents, proxyDependencyClaim)
            if (alreadyGenerated != null) {
                stack.removeLast()
                prevFrame.resolvedDependencies.add(alreadyGenerated)
                stack.addLast(prevFrame.copy(currentDependency = prevFrame.currentDependency + 1))
                return true
            }
            var proxyComponentDeclaration = GraphResolutionHelper.findDependencyDeclarationFromTemplate(ctx, declaration, templateDeclarations, proxyDependencyClaim)
            if (proxyComponentDeclaration == null) {
                proxyComponentDeclaration = generatePromisedProxy(ctx, claimTypeDeclaration)
                if (claimTypeDeclaration.typeParameters.isNotEmpty()) {
                    templateDeclarations.add(proxyComponentDeclaration)
                } else {
                    sourceDeclarations.add(proxyComponentDeclaration)
                }
            }
            val proxyResolvedComponent = ResolvedComponent(
                resolvedComponents.size,
                proxyComponentDeclaration,
                dependencyClaim.type,
                setOf(CommonClassNames.promisedProxy.canonicalName),
                emptyList(),
                listOf(
                    ComponentDependency.PromisedProxyParameterDependency(
                        declaration, DependencyClaim(
                            declaration.type,
                            declaration.tags,
                            ONE_REQUIRED
                        )
                    )
                )
            )
            resolvedComponents.add(proxyResolvedComponent)
            return true
        }
        return false
    }

}
