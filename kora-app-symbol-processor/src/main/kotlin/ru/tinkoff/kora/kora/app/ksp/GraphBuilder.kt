package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.*
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
                            results.add(
                                ProcessingState.NewRoundRequired(
                                    e.source,
                                    e.type,
                                    e.tag,
                                    e.resolving
                                )
                            )
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
                    val type = dependencyClaim.type.arguments[0].type!!.resolve().makeNullable()
                    val claim = ComponentDependencyHelper.parseClaim(type, dependencyClaim.tags, declaration.source)
                    stack.addLast(
                        ProcessingState.ResolutionFrame.Component(
                            optionalDeclaration, listOf(
                                claim
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
                    } else if (extensionResult is ExtensionResult.CodeBlockResult) {
                        val extensionComponent = ComponentDeclaration.fromExtension(extensionResult)
                        if (extensionComponent.isTemplate()) {
                            processing.templateDeclarations.add(extensionComponent)
                        } else {
                            processing.sourceDeclarations.add(extensionComponent)
                        }
                        stack.addLast(frame.copy(currentDependency = currentDependency))
                        continue@frame
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
                val msg = if (dependencyClaim.tags.isEmpty()) {
                    StringBuilder(
                        "Required dependency type wasn't found in graph and can't be auto created: ${dependencyClaim.type.toTypeName()} (no tags)\n" +
                            "Keep in mind that nullable & non nullable types are different in Kotlin.\n" +
                            "Please check class for @${CommonClassNames.component.canonicalName} annotation or that required module with component factory is plugged in."
                    )
                } else {
                    val tagMsg = dependencyClaim.tags.joinToString(", ", "@Tag(", ")")
                    StringBuilder(
                        "Required dependency type wasn't found in graph and can't be auto created: ${dependencyClaim.type.toTypeName()} with tag ${tagMsg}.\n" +
                            "Keep in mind that nullable & non nullable types are different in Kotlin).\n" +
                            "Please check class for @${CommonClassNames.component.canonicalName} annotation or that required module with component factory is plugged in."
                    )
                }

                if (hints.isNotEmpty()) {
                    msg.append("\n\nHints:")
                    for (hint in hints) {
                        msg.append("\n  - Hint: ").append(hint.message())
                    }
                }

                val claimMsg = "Required dependency claim: $dependencyClaim"
                msg.append("\n\n").append(claimMsg)

                val requestedMsg = getRequestedMessage(declaration)
                msg.append("\n").append(requestedMsg)

                val treeMsg = getDependencyTreeSimpleMessage(stack, declaration, dependencyClaim, processing)
                msg.append("\n").append(treeMsg)

                throw UnresolvedDependencyException(
                    msg.toString(),
                    declaration,
                    dependencyClaim
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

    private fun getRequestedMessage(declaration: ComponentDeclaration): String {
        var element: KSDeclaration? = declaration.source
        var factoryMethod: KSFunctionDeclaration? = null
        var module: KSDeclaration? = null
        do {
            if (element is KSFunctionDeclaration) {
                factoryMethod = element
            } else if (element is KSClassDeclaration) {
                module = element
                break
            } else if (element == null) {
                continue
            }
            element = element.parentDeclaration
        } while (element != null)

        return if (module != null && factoryMethod != null && factoryMethod.isConstructor()) {
            "Dependency requested at: ${module.qualifiedName!!.asString()}#${factoryMethod}(${
                factoryMethod.parameters.joinToString(", ") {
                    it.type.toTypeName().toString()
                }
            })"
        } else {
            "Dependency requested at: ${module!!.qualifiedName!!.asString()}#${factoryMethod!!.qualifiedName!!.asString()}(${
                factoryMethod.parameters.joinToString(", ") {
                    it.type.toTypeName().toString()
                }
            })"
        }
    }

    private fun getDependencyTreeSimpleMessage(
        stack: Deque<ProcessingState.ResolutionFrame>,
        declaration: ComponentDeclaration,
        dependencyClaim: DependencyClaim,
        processing: ProcessingState.Processing
    ): String {
        val msg = StringBuilder()
        msg.append("Dependency resolution tree:")

        val stackFrames = mutableListOf<ProcessingState.ResolutionFrame>()
        val i = stack.descendingIterator()
        while (i.hasNext()) {
            val iFrame: ProcessingState.ResolutionFrame = i.next()
            if (iFrame is ProcessingState.ResolutionFrame.Root) {
                stackFrames.add(iFrame)
                break
            }
            stackFrames.add(iFrame)
        }

        // reversed order
        val delimiterRoot = "\n  @--- "
        val delimiterOverriden = "\n  ^~~~ "
        val delimiter = "\n  ^--- "
        for (i1 in stackFrames.indices.reversed()) {
            val iFrame = stackFrames[i1]
            if (iFrame is ProcessingState.ResolutionFrame.Root) {
                val rootDeclaration = processing.rootSet[iFrame.rootIndex]
                val rootDeclarationAsStr = rootDeclaration.declarationString()
                if (rootDeclaration is ComponentDeclaration.FromModuleComponent) {
                    val currentModuleTypeName = rootDeclaration.module.element.qualifiedName!!.asString()
                    val originalModuleTypeName = if (rootDeclaration.method.findOverridee()?.parentDeclaration != null) {
                        rootDeclaration.method.findOverridee()!!.parentDeclaration!!.qualifiedName!!.asString()
                    } else {
                        rootDeclaration.module.element.qualifiedName!!.asString()
                    }

                    if (currentModuleTypeName != originalModuleTypeName) {
                        msg.append(delimiterRoot).append(rootDeclarationAsStr.replace(originalModuleTypeName, currentModuleTypeName))
                        msg.append(delimiter).append(rootDeclarationAsStr)
                    } else {
                        msg.append(delimiterRoot).append(rootDeclarationAsStr)
                    }
                } else {
                    msg.append(delimiterRoot).append(rootDeclarationAsStr)
                }
            } else {
                val c = iFrame as ProcessingState.ResolutionFrame.Component
                if (c.declaration is ComponentDeclaration.FromModuleComponent && c.declaration.isOverriden()) {
                    msg.append(delimiterOverriden).append(c.declaration.declarationString())
                } else {
                    msg.append(delimiter).append(c.declaration.declarationString())
                }
            }
        }

        msg.append(delimiter).append(declaration.declarationString())

        val errorMissing = " [ ERROR: MISSING COMPONENT ]"
        if (dependencyClaim.tags.isEmpty()) {
            msg.append(delimiter)
                .append(dependencyClaim.type.toTypeName()).append("   ")
                .append(errorMissing)
        } else {
            msg.append(delimiter)
                .append(dependencyClaim.type.toTypeName())
                .append("  @Tag").append(dependencyClaim.tags.stream().collect(Collectors.joining(", ", "(", ")"))).append("   ")
                .append(errorMissing)
        }

        return msg.toString()
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
