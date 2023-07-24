package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependency
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim.DependencyClaimType.*
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.kora.app.ksp.exception.CircularDependencyException
import ru.tinkoff.kora.kora.app.ksp.exception.NewRoundException
import ru.tinkoff.kora.kora.app.ksp.exception.UnresolvedDependencyException
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import java.util.*
import java.util.stream.Collectors

object GraphBuilder {
    fun processProcessing(ctx: ProcessingContext, p: ProcessingState.Processing, forClaim: DependencyClaim? = null): ProcessingState {
        if (p.rootSet.isEmpty()) {
            return ProcessingState.Failed(
                ProcessingErrorException(
                    "@KoraApp has no root components, expected at least one component annotated with @Root",
                    p.root
                ),
                p.resolutionStack
            )
        }
        var processing = p;
        var stack = processing.resolutionStack
        frame@ while (stack.isNotEmpty()) {
            val frame = stack.removeLast()
            if (frame is ProcessingState.ResolutionFrame.Root) {
                val declaration = processing.rootSet[frame.rootIndex]
                if (processing.findResolvedComponent(declaration) != null) {
                    continue
                }
                stack.addLast(ProcessingState.ResolutionFrame.Component(declaration))
                stack.addAll(findInterceptors(ctx, processing, declaration))
                continue
            }
            frame as ProcessingState.ResolutionFrame.Component
            val declaration = frame.declaration
            val dependenciesToFind = frame.dependenciesToFind
            val resolvedDependencies = frame.resolvedDependencies
            if (checkCycle(ctx, processing, declaration)) {
                continue
            }

            dependency@ for (currentDependency in frame.currentDependency until dependenciesToFind.size) {
                val dependencyClaim = dependenciesToFind[currentDependency]
                ctx.kspLogger.info("Resolving ${dependencyClaim.type} for ${declaration.source}")
                if (dependencyClaim.claimType in listOf(ALL, ALL_OF_PROMISE, ALL_OF_VALUE)) {
                    val allOfDependency = processAllOf(ctx, processing, frame, currentDependency)
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
                val dependencyComponent = GraphResolutionHelper.findDependency(ctx, declaration, processing.resolvedComponents, dependencyClaim)
                if (dependencyComponent != null) {
                    resolvedDependencies.add(dependencyComponent)
                    continue@dependency
                }
                val dependencyDeclaration = GraphResolutionHelper.findDependencyDeclaration(ctx, declaration, processing.sourceDeclarations, dependencyClaim)
                if (dependencyDeclaration != null) {
                    stack.addLast(frame.copy(currentDependency = currentDependency))
                    stack.addLast(ProcessingState.ResolutionFrame.Component(dependencyDeclaration))
                    stack.addAll(findInterceptors(ctx, processing, dependencyDeclaration))
                    continue@frame
                }
                val templates = GraphResolutionHelper.findDependencyDeclarationsFromTemplate(ctx, declaration, processing.templateDeclarations, dependencyClaim)
                if (templates.isNotEmpty()) {
                    if (templates.size == 1) {
                        val template = templates[0]
                        processing.sourceDeclarations.add(template)
                        stack.addLast(frame.copy(currentDependency = currentDependency))
                        stack.addLast(ProcessingState.ResolutionFrame.Component(template))
                        stack.addAll(findInterceptors(ctx, processing, template))
                        continue@frame
                    }
                    val results = ArrayList<ProcessingState>(templates.size)
                    var exception: UnresolvedDependencyException? = null
                    for (template in templates) {
                        val newProcessing: ProcessingState.Processing = ProcessingState.Processing(
                            processing.root,
                            processing.allModules,
                            ArrayList(processing.sourceDeclarations),
                            ArrayList(processing.templateDeclarations),
                            processing.rootSet,
                            ArrayList(processing.resolvedComponents),
                            ArrayDeque(processing.resolutionStack)
                        )
                        newProcessing.sourceDeclarations.add(template)
                        newProcessing.resolutionStack.addLast(frame.copy(currentDependency = currentDependency))
                        newProcessing.resolutionStack.addLast(ProcessingState.ResolutionFrame.Component(template))
                        newProcessing.resolutionStack.addAll(this.findInterceptors(ctx, processing, template))
                        try {
                            results.add(this.processProcessing(ctx, newProcessing, dependencyClaim))
                        } catch (e: NewRoundException) {
                            results.add(ProcessingState.NewRoundRequired(
                                e.source,
                                e.type,
                                e.tag,
                                e.resolving
                            ))
                        } catch (e: UnresolvedDependencyException) {
                            if (exception != null) {
                                exception.addSuppressed(e)
                            } else {
                                exception = e
                            }
                        }
                    }
                    if (results.size == 1) {
                        val result = results[0]
                        if (result is ProcessingState.Processing) {
                            stack = result.resolutionStack
                            processing = result
                            continue@frame
                        } else {
                            return result
                        }
                    }
                    if (results.size > 1) {
                        val deps = templates.stream().map { Objects.toString(it) }
                            .collect(Collectors.joining("\n"))
                            .prependIndent("  ")
                        throw ProcessingErrorException(
                            """
                            More than one component matches dependency claim ${dependencyClaim.type}:
                            $deps
                            """.trimIndent(), declaration.source
                        )
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
                    processing.sourceDeclarations.add(optionalDeclaration)
                    stack.addLast(frame.copy(currentDependency = currentDependency))
                    stack.addLast(
                        ProcessingState.ResolutionFrame.Component(
                            optionalDeclaration, listOf(
                                DependencyClaim(
                                    dependencyClaim.type.arguments[0].type!!.resolve().makeNotNullable(),
                                    dependencyClaim.tags,
                                    NULLABLE_ONE
                                )
                            )
                        )
                    )
                    continue@frame
                }
                val finalClassComponent = GraphResolutionHelper.findFinalDependency(ctx, dependencyClaim)
                if (finalClassComponent != null) {
                    processing.sourceDeclarations.add(finalClassComponent)
                    stack.addLast(frame.copy(currentDependency = currentDependency))
                    stack.addLast(ProcessingState.ResolutionFrame.Component(finalClassComponent))
                    stack.addAll(findInterceptors(ctx, processing, finalClassComponent))
                    continue@frame
                }
                val extension = ctx.extensions.findExtension(ctx.resolver, dependencyClaim.type, dependencyClaim.tags)
                if (extension != null) {
                    val extensionResult = extension()
                    if (extensionResult is ExtensionResult.RequiresCompilingResult) {
                        stack.addLast(frame.copy(currentDependency = currentDependency))
                        throw NewRoundException(processing, extension, dependencyClaim.type, dependencyClaim.tags)
                    } else {
                        extensionResult as ExtensionResult.GeneratedResult
                        val extensionComponent = ComponentDeclaration.fromExtension(ctx, extensionResult)
                        if (extensionComponent.isTemplate()) {
                            processing.templateDeclarations.add(extensionComponent)
                        } else {
                            processing.sourceDeclarations.add(extensionComponent)
                        }
                        stack.addLast(frame.copy(currentDependency = currentDependency))
                        continue@frame
                    }
                }
                val hints = ctx.dependencyHintProvider.findHints(dependencyClaim.type, dependencyClaim.tags)
                val msg = if(dependencyClaim.tags.isEmpty()) {
                    StringBuilder(
                        "Required dependency type was not found and can't be auto created: ${dependencyClaim.type.toTypeName()}.\n" +
                            "Please check class for @${CommonClassNames.component.canonicalName} annotation or that required module with component is plugged in."
                    )
                } else {
                    val tagMsg = dependencyClaim.tags.joinToString(", ", "@Tag(", ")")
                    StringBuilder(
                        "Required dependency type was not found and can't be auto created: ${dependencyClaim.type.toTypeName()} with tag ${tagMsg}.\n" +
                            "Please check class for @${CommonClassNames.component.canonicalName} annotation or that required module with component is plugged in."
                    )
                }
                for (hint in hints) {
                    msg.append("\n  Hint: ").append(hint.message())
                }
                throw UnresolvedDependencyException(
                    msg.toString(),
                    declaration.source,
                    dependencyClaim.type,
                    dependencyClaim.tags
                )
            }
            processing.resolvedComponents.add(
                ResolvedComponent(
                    processing.resolvedComponents.size,
                    declaration,
                    declaration.type,
                    declaration.tags,
                    listOf(),
                    resolvedDependencies
                )
            )
            if (forClaim != null) {
                if (forClaim.tagsMatches(declaration.tags) && forClaim.type.isAssignableFrom(declaration.type)) {
                    return processing
                }
            }
        }
        return ProcessingState.Ok(processing.root, processing.allModules, ArrayList(processing.resolvedComponents))
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

    private fun processAllOf(ctx: ProcessingContext, processing: ProcessingState.Processing, componentFrame: ProcessingState.ResolutionFrame.Component, currentDependency: Int): ComponentDependency? {
        val dependencyClaim = componentFrame.dependenciesToFind[currentDependency]
        val dependencies = GraphResolutionHelper.findDependencyDeclarations(ctx, processing.sourceDeclarations, dependencyClaim)
        for (dependency in dependencies) {
            if (dependency.isDefault()) {
                continue
            }
            val resolved = processing.findResolvedComponent(dependency)
            if (resolved != null) {
                continue
            }
            processing.resolutionStack.addLast(componentFrame.copy(currentDependency = currentDependency))
            processing.resolutionStack.addLast(ProcessingState.ResolutionFrame.Component(dependency))
            processing.resolutionStack.addAll(findInterceptors(ctx, processing, dependency))
            return null
        }
        if (dependencyClaim.claimType == ALL || dependencyClaim.claimType == ALL_OF_VALUE || dependencyClaim.claimType == ALL_OF_PROMISE) {
            return ComponentDependency.AllOfDependency(dependencyClaim)
        }
        throw IllegalStateException()
    }

    private fun findInterceptors(ctx: ProcessingContext, processing: ProcessingState.Processing, declaration: ComponentDeclaration): List<ProcessingState.ResolutionFrame.Component> {
        return GraphResolutionHelper.findInterceptorDeclarations(ctx, processing.sourceDeclarations, declaration.type)
            .asSequence()
            .filter { id -> processing.resolvedComponents.none { it.declaration === id } && processing.resolutionStack.none { it is ProcessingState.ResolutionFrame.Component && it.declaration == id } }
            .map { ProcessingState.ResolutionFrame.Component(it) }
            .toList()

    }

    private fun generatePromisedProxy(ctx: ProcessingContext, claimTypeDeclaration: KSClassDeclaration): ComponentDeclaration {
        val resultClassName = claimTypeDeclaration.getOuterClassesAsPrefix() + claimTypeDeclaration.simpleName.asString() + "_PromisedProxy"
        val typeTpr = claimTypeDeclaration.typeParameters.toTypeParameterResolver()
        val typeName = claimTypeDeclaration.toClassName().parameterizedBy(claimTypeDeclaration.typeParameters.map { it.toTypeVariableName(typeTpr) })
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
            .addFunction(FunSpec.builder("getDelegate")
                .addModifiers(KModifier.PRIVATE)
                .returns(typeName)
                .addCode(CodeBlock.builder()
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
            if (!fn.isOpen()) {
                continue
            }
            if (fn.simpleName.asString() in setOf("equals", "hashCode", "toString")) {
                continue // todo figure out a better way to handle this
            }
            val funTpr = fn.typeParameters.toTypeParameterResolver(typeTpr)
            val method = FunSpec.builder(fn.simpleName.getShortName())
                .addModifiers(KModifier.OVERRIDE)
                .returns(fn.returnType!!.resolve().toTypeName(funTpr))
            method.addCode("return this.getDelegate().%L(", fn.simpleName.getShortName())
            for ((i, param) in fn.parameters.withIndex()) {
                if (i > 0) {
                    method.addCode(", ")
                }
                method.addCode("%L", param.name!!.getShortName())
                method.addParameter(param.name!!.getShortName(), param.type.toTypeName(funTpr))
            }
            method.addCode(")\n")
            type.addFunction(method.build())
        }

        val packageName = claimTypeDeclaration.packageName.asString()
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

    private fun checkCycle(ctx: ProcessingContext, processing: ProcessingState.Processing, declaration: ComponentDeclaration): Boolean {
        val prevFrame = processing.resolutionStack.peekLast()
        if (prevFrame !is ProcessingState.ResolutionFrame.Component) {
            return false
        }
        if (prevFrame.dependenciesToFind.isEmpty()) {
            return false
        }
        val dependencyClaim = prevFrame.dependenciesToFind[prevFrame.currentDependency]
        val claimTypeDeclaration = dependencyClaim.type.declaration
        for (frame in processing.resolutionStack) {
            if (frame !is ProcessingState.ResolutionFrame.Component || frame.declaration !== declaration) {
                continue
            }
            val circularDependencyException = CircularDependencyException(listOf(prevFrame.declaration.toString(), declaration.toString()), frame.declaration)
            if (claimTypeDeclaration !is KSClassDeclaration) throw circularDependencyException
            if (claimTypeDeclaration.classKind != ClassKind.INTERFACE && !(claimTypeDeclaration.classKind == ClassKind.CLASS && claimTypeDeclaration.isOpen())) throw circularDependencyException
            val proxyDependencyClaim = DependencyClaim(
                dependencyClaim.type, setOf(CommonClassNames.promisedProxy.canonicalName), dependencyClaim.claimType
            )
            val alreadyGenerated = GraphResolutionHelper.findDependency(ctx, prevFrame.declaration, processing.resolvedComponents, proxyDependencyClaim)
            if (alreadyGenerated != null) {
                processing.resolutionStack.removeLast()
                prevFrame.resolvedDependencies.add(alreadyGenerated)
                processing.resolutionStack.addLast(prevFrame.copy(currentDependency = prevFrame.currentDependency + 1))
                return true
            }
            var proxyComponentDeclaration = GraphResolutionHelper.findDependencyDeclarationFromTemplate(ctx, declaration, processing.templateDeclarations, proxyDependencyClaim)
            if (proxyComponentDeclaration == null) {
                proxyComponentDeclaration = generatePromisedProxy(ctx, claimTypeDeclaration)
                if (claimTypeDeclaration.typeParameters.isNotEmpty()) {
                    processing.templateDeclarations.add(proxyComponentDeclaration)
                } else {
                    processing.sourceDeclarations.add(proxyComponentDeclaration)
                }
            }
            val proxyResolvedComponent = ResolvedComponent(
                processing.resolvedComponents.size,
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
            processing.resolvedComponents.add(proxyResolvedComponent)
            return true
        }
        return false
    }

}
