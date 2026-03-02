package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.kora.app.ksp.KoraAppUtils.validateComponent
import ru.tinkoff.kora.kora.app.ksp.component.ComponentDependency
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponent
import ru.tinkoff.kora.kora.app.ksp.component.ResolvedComponents
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclarations
import ru.tinkoff.kora.kora.app.ksp.declaration.ModuleDeclaration
import ru.tinkoff.kora.kora.app.ksp.exception.UnresolvedDependencyException
import ru.tinkoff.kora.kora.app.ksp.interceptor.ComponentInterceptors
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonAopUtils.hasAopAnnotations
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import java.util.function.Supplier

class KoraAppProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    companion object {
        const val COMPONENTS_PER_HOLDER_CLASS = 500
    }


    private val codeGenerator = environment.codeGenerator
    private val annotatedModules = mutableListOf<String>()
    private val components = mutableListOf<String>()
    private val koraApps = mutableListOf<String>()

    private var resolver: Resolver? = null
    private var hasDeferred = false

    override fun finish() {
        if (hasDeferred) {
            kspLogger.warn("Kora app wasn't processed because some symbols are not valid")
            return
        }
        val ctx = ProcessingContext(resolver!!, kspLogger, codeGenerator)
        for (fullName in koraApps) {
            val element = resolver!!.getClassDeclarationByName(fullName)!!
            try {
                val graph = buildGraph(ctx, element)
                write(ctx, element, graph.allModules, graph.declarations, graph.components)
            } catch (e: UnresolvedDependencyException) {
                e.printError(kspLogger)
                kspLogger.info("Dependency detailed resolution tree:\n\n${e.errors.first().message}")
            } catch (e: ProcessingErrorException) {
                e.printError(kspLogger)
            }
        }
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        this.resolver = resolver

        this.processModules(resolver).let { deferred.addAll(it) }
        try {
            this.processComponents(resolver).let { deferred.addAll(it) }
        } catch (e: ProcessingErrorException) {
            e.printError(kspLogger)
            return deferred
        }

        if (deferred.isNotEmpty()) {
            deferred.addAll(resolver.getSymbolsWithAnnotation(CommonClassNames.koraApp.canonicalName))
            return deferred
        }

        val koraAppElements = resolver.getSymbolsWithAnnotation(CommonClassNames.koraApp.canonicalName).toList()

        for (declaration in koraAppElements) {
            if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE) {
                if (declaration.validateAll()) {
                    kspLogger.info("@KoraApp found: ${declaration.qualifiedName!!.asString()}", declaration)
                    koraApps.add(declaration.qualifiedName!!.asString())
                } else {
                    deferred.add(declaration)
                }
            } else {
                kspLogger.error("@KoraApp can be placed only on interfaces", declaration)
            }
        }
        hasDeferred = deferred.isNotEmpty()
        return deferred
    }

    private fun buildGraph(ctx: ProcessingContext, declaration: KSClassDeclaration): ResolvedGraph {
        if (declaration.classKind != ClassKind.INTERFACE) {
            throw ProcessingErrorException("@KoraApp is only applicable to interfaces", declaration)
        }
        val rootErasure = declaration.asStarProjectedType()
        val rootModule = ModuleDeclaration.MixedInModule(declaration)
        val filterObjectMethods: (KSFunctionDeclaration) -> Boolean = {
            val name = it.simpleName.asString()
            !it.modifiers.contains(Modifier.PRIVATE)
                && name != "equals"
                && name != "hashCode"
                && name != "toString"// todo find out a better way to filter object methods
        }
        val mixedInComponents = declaration.getAllFunctions()
            .filter(filterObjectMethods)
            .toMutableList()

        val allInterfaces = declaration.getAllSuperTypes().toList()
        val submodules = findKoraSubmoduleModules(ctx.resolver, allInterfaces, declaration)
        val allModules = (submodules + annotatedModules.map { resolver!!.getClassDeclarationByName(it)!! })
            .flatMap { it.getAllSuperTypes().map { it.declaration as KSClassDeclaration } + it }
            .filter { it.qualifiedName?.asString() != "kotlin.Any" }
            .toSet()
            .toList()

        val annotatedModules = allModules
            .filter { !it.asStarProjectedType().isAssignableFrom(rootErasure) }
            .map { ModuleDeclaration.AnnotatedModule(it) }
        val annotatedModuleComponentsTmp = annotatedModules
            .flatMap { it.element.getDeclaredFunctions().filter(filterObjectMethods).map { f -> ComponentDeclaration.fromModule(ctx, it, f) } }
        val annotatedModuleComponents = ArrayList(annotatedModuleComponentsTmp)
        for (annotatedComponent in annotatedModuleComponentsTmp) {
            if (annotatedComponent.method.modifiers.contains(Modifier.OVERRIDE)) {
                val overridee = annotatedComponent.method.findOverridee()
                annotatedModuleComponents.removeIf { it.method == overridee }
                mixedInComponents.remove(overridee)
            }
        }
        val allComponents = ArrayList<ComponentDeclaration>(annotatedModuleComponents.size + mixedInComponents.size + 200)
        for (componentClass in components) {
            val decl = ctx.resolver.getClassDeclarationByName(componentClass)!!
            allComponents.add(ComponentDeclaration.fromAnnotated(ctx, decl))
        }
        allComponents.addAll(mixedInComponents.asSequence().map { ComponentDeclaration.fromModule(ctx, rootModule, it) })
        allComponents.addAll(annotatedModuleComponents)
        allComponents.sortedBy { it.toString() }
        // todo modules from kora app part
        val templateComponents = ArrayList<ComponentDeclaration>(allComponents.size)
        val components = ArrayList<ComponentDeclaration>(allComponents.size)
        for (component in allComponents) {
            if (component.isTemplate()) {
                templateComponents.add(component)
            } else {
                components.add(component)
            }
        }
        val b = GraphBuilder(ctx!!, declaration, allModules, components, templateComponents)
        return b.build()
    }

    private fun findKoraSubmoduleModules(resolver: Resolver, supers: List<KSType>, koraApp: KSClassDeclaration): List<KSClassDeclaration> {
        return supers
            .map { it.declaration as KSClassDeclaration }
            .filter {
                when {
                    it.findAnnotation(CommonClassNames.koraSubmodule) != null -> true
                    it.findAnnotation(CommonClassNames.koraApp) != null && it != koraApp -> true
                    else -> false
                }
            }
            .mapNotNull {
                val ksName = resolver.getKSNameFromString(it.qualifiedName!!.asString() + "SubmoduleImpl")
                if (it.findAnnotation(CommonClassNames.koraApp) != null) {
                    val classDeclarationByName = resolver.getClassDeclarationByName(ksName)
                    if (classDeclarationByName == null) {
                        kspLogger.warn(
                            """
                                Expected @KoraApp as SubModule, but Submodule implementation not found for: ${it.toClassName().canonicalName}
                                Check that @KoraApp was generated with KSP argument: kora.app.submodule.enabled=true
                                """.trimIndent(), it
                        )
                    }
                    classDeclarationByName
                } else {
                    resolver.getClassDeclarationByName(ksName) ?: throw ProcessingErrorException("Declaration of ${ksName.asString()} wasn't found", it)
                }
            }
            .toList()
    }

    private fun processModules(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val moduleOfSymbols = resolver.getSymbolsWithAnnotation(CommonClassNames.module.canonicalName)
        for (module in moduleOfSymbols) {
            if (module is KSClassDeclaration && module.classKind == ClassKind.INTERFACE) {
                if (module.validateAll()) {
                    annotatedModules.add(module.qualifiedName!!.asString())
                } else {
                    deferred.add(module)
                }
            }
        }
        return deferred
    }

    private fun processComponents(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val componentOfSymbols = resolver.getSymbolsWithAnnotation(CommonClassNames.component.canonicalName)

        for (componentSymbol in componentOfSymbols) {
            if (componentSymbol is KSClassDeclaration && componentSymbol.classKind == ClassKind.CLASS && !componentSymbol.modifiers.contains(Modifier.ABSTRACT)) {
                if (!hasAopAnnotations(componentSymbol)) {
                    if (componentSymbol.validateAll() && componentSymbol.validateComponent()) {
                        components.add(componentSymbol.qualifiedName!!.asString())
                    } else {
                        deferred.add(componentSymbol)
                    }
                }
            }
        }
        return deferred
    }


    private fun write(
        ctx: ProcessingContext,
        declaration: KSClassDeclaration,
        allModules: List<KSClassDeclaration>,
        declarations: ComponentDeclarations,
        components: ResolvedComponents
    ) {
        val interceptors: ComponentInterceptors = ComponentInterceptors.parseInterceptors(ctx, components.components())
        kspLogger.logging("Found interceptors: $interceptors")
        val applicationImplFile = this.generateImpl(declaration, allModules)
        val applicationGraphFile = this.generateApplicationGraph(ctx, declaration, allModules, interceptors, declarations, components)
        applicationImplFile.writeTo(codeGenerator = codeGenerator, Dependencies.ALL_FILES)
        applicationGraphFile.writeTo(codeGenerator = codeGenerator, Dependencies.ALL_FILES)
    }

    private fun generateImpl(declaration: KSClassDeclaration, modules: List<KSClassDeclaration>): FileSpec {
        val packageName = declaration.packageName.asString()
        val moduleName = "\$${declaration.toClassName().simpleName}Impl"

        val fileSpec = FileSpec.builder(
            packageName = packageName,
            fileName = moduleName
        )
        val classBuilder = TypeSpec.classBuilder(moduleName)
            .generated(KoraAppProcessor::class)
            .addModifiers(KModifier.PUBLIC, KModifier.OPEN)
            .addSuperinterface(declaration.toClassName())

        for ((index, module) in modules.withIndex()) {
            val moduleClass = module.toClassName()
            classBuilder.addProperty(
                PropertySpec.builder("module$index", moduleClass)
                    .initializer("@%T(%S) object : %T {}", CommonClassNames.generated, KoraAppProcessor::class.qualifiedName, moduleClass)
                    .build()
            )
        }
        return fileSpec.addType(classBuilder.build()).build()
    }

    private fun generateApplicationGraph(
        ctx: ProcessingContext,
        declaration: KSClassDeclaration,
        allModules: List<KSClassDeclaration>,
        interceptors: ComponentInterceptors,
        declarations: ComponentDeclarations,
        components: ResolvedComponents
    ): FileSpec {
        val packageName = declaration.packageName.asString()
        val graphName = "${declaration.simpleName.asString()}Graph"
        val graphTypeName = ClassName(packageName, graphName)

        val fileSpec = FileSpec.builder(
            packageName = packageName,
            fileName = graphName
        )

        val implClass = ClassName(packageName, "\$${declaration.simpleName.asString()}Impl")
        val supplierSuperInterface = Supplier::class.asClassName().parameterizedBy(CommonClassNames.applicationGraphDraw)
        val classBuilder = TypeSpec.classBuilder(graphName)
            .generated(KoraAppProcessor::class)
            .addSuperinterface(supplierSuperInterface)
            .addFunction(
                FunSpec.builder("get")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(CommonClassNames.applicationGraphDraw)
                    .addStatement("return graphDraw")
                    .build()
            )

        val companion = TypeSpec.companionObjectBuilder()
            .generated(KoraAppProcessor::class)
            .addProperty("graphDraw", CommonClassNames.applicationGraphDraw)

        var currentClass: TypeSpec.Builder? = null
        var currentConstructor: FunSpec.Builder? = null
        var holders = 0

        for ((i, component) in components.components().withIndex()) {
            val componentNumber = i % COMPONENTS_PER_HOLDER_CLASS
            if (componentNumber == 0) {
                if (currentClass != null) {
                    currentClass.primaryConstructor(currentConstructor!!.build())
                    classBuilder.addType(currentClass.build())
                    val prevNumber = i / COMPONENTS_PER_HOLDER_CLASS - 1
                    companion.addProperty("holder$prevNumber", graphTypeName.nestedClass("ComponentHolder$prevNumber"))
                }
                holders++
                val className = graphTypeName.nestedClass("ComponentHolder" + i / COMPONENTS_PER_HOLDER_CLASS)
                currentClass = TypeSpec.classBuilder(className)
                    .generated(KoraAppProcessor::class)
                currentConstructor = FunSpec.constructorBuilder()
                    .addParameter("graphDraw", CommonClassNames.applicationGraphDraw)
                    .addParameter("impl", implClass)
                    .addStatement("val self = %T", graphTypeName)
                    .addStatement("val map = %T<%T, %T>()", HashMap::class.asClassName(), String::class.asClassName(), Type::class.asClassName())
                    .controlFlow("for (field in %T::class.java.declaredFields)", className) {
                        controlFlow("if (!field.name.startsWith(%S))", "component") { addStatement("continue") }
                        addStatement("map[field.name] = (field.genericType as %T).actualTypeArguments[0]", ParameterizedType::class.asClassName())
                    }
                for (j in 0 until i / COMPONENTS_PER_HOLDER_CLASS) {
                    currentConstructor.addParameter("ComponentHolder$j", graphTypeName.nestedClass("ComponentHolder$j"));
                }
            }

            val aopProxySuperClass = ServiceTypesHelper.findAopProxySuperClass(component.type)
            val propertyType: TypeName = aopProxySuperClass?.toTypeName() ?: component.type.toTypeName()

            currentClass!!.addProperty(component.fieldName, CommonClassNames.node.parameterizedBy(propertyType))
            val statement = this.generateComponentStatement(ctx, allModules, interceptors, component, declarations, components)
            currentConstructor!!.addCode(statement).addCode("\n")
        }
        if (components.size > 0) {
            var lastComponentNumber = components.size / COMPONENTS_PER_HOLDER_CLASS;
            if (components.size % COMPONENTS_PER_HOLDER_CLASS == 0) {
                lastComponentNumber--
            }
            currentClass!!.addFunction(currentConstructor!!.build());
            classBuilder.addType(currentClass.build())
            companion.addProperty("holder$lastComponentNumber", graphTypeName.nestedClass("ComponentHolder$lastComponentNumber"));
        }


        val initBlock = CodeBlock.builder()
            .addStatement("val self = %T", graphTypeName)
            .addStatement("val impl = %T()", implClass)
            .addStatement("graphDraw =  %T(%T::class.java)", CommonClassNames.applicationGraphDraw, declaration.toClassName())
        for (i in 0 until holders) {
            initBlock.add("%N = %T(graphDraw, impl", "holder$i", graphTypeName.nestedClass("ComponentHolder$i"))
            for (j in 0 until i) {
                initBlock.add(", holder$j")
            }
            initBlock.add(")\n");
        }

        val supplierMethodBuilder = FunSpec.builder("graph")
            .returns(CommonClassNames.applicationGraphDraw)
            .addCode("\nreturn graphDraw\n", declaration.simpleName.asString() + "Graph")
        return fileSpec.addType(
            classBuilder
                .addType(companion.addInitializerBlock(initBlock.build()).addFunction(supplierMethodBuilder.build()).build())
                .addFunction(supplierMethodBuilder.build())
                .build()
        ).build()
    }

    private fun generateComponentStatement(
        ctx: ProcessingContext,
        allModules: List<KSClassDeclaration>,
        interceptors: ComponentInterceptors,
        component: ResolvedComponent,
        declarations: ComponentDeclarations,
        components: ResolvedComponents
    ): CodeBlock {
        val statement = CodeBlock.builder()
        val declaration = component.declaration
        statement.add("%N = graphDraw.addNode0(map[%S], ", component.fieldName, component.fieldName)
        statement.indent().add("\n")
        if (component.tag == null) {
            statement.add("null,\n")
        } else {
            statement.add("%L::class.java,\n", component.tag)
        }
        statement.add("{ ")
        val dependenciesCode = this.getDependenciesCode(ctx, component, declarations, components)

        when (declaration) {
            is ComponentDeclaration.AnnotatedComponent -> {
                statement.add("%T", declaration.classDeclaration.toClassName())
                if (declaration.typeVariables.isNotEmpty()) {
                    statement.add("<")
                    for ((i, tv) in declaration.typeVariables.withIndex()) {
                        if (i > 0) {
                            statement.add(", ")
                        }
                        statement.add("%L", tv.type!!.toTypeName())
                    }
                    statement.add(">")
                }
                statement.add("(%L)", dependenciesCode)
            }

            is ComponentDeclaration.FromModuleComponent -> {
                if (declaration.module is ModuleDeclaration.AnnotatedModule) {
                    statement.add("impl.module%L.", allModules.indexOf(declaration.module.element))
                } else {
                    statement.add("impl.")
                }
                statement.add("%N", declaration.method.simpleName.asString())
                if (declaration.typeVariables.isNotEmpty()) {
                    statement.add("<")
                    for ((i, tv) in declaration.typeVariables.withIndex()) {
                        if (i > 0) {
                            statement.add(", ")
                        }
                        statement.add("%L", tv.type!!.toTypeName())
                    }
                    statement.add(">")
                }
                statement.add("(%L)", dependenciesCode)
            }

            is ComponentDeclaration.FromExtensionComponent -> {
                statement.add(declaration.generator(dependenciesCode))
            }

            is ComponentDeclaration.PromisedProxyComponent -> {
                statement.add("%T(%L)", declaration.className, dependenciesCode)
            }

            is ComponentDeclaration.OptionalComponent -> {
                statement.add("%T.ofNullable(%L)", Optional::class.asClassName(), dependenciesCode)
            }
        }
        statement.add(" },\n")
        statement.add("listOf(")
        for ((i, interceptor) in interceptors.interceptorsFor(declaration).withIndex()) {
            if (i > 0) {
                statement.add(", ")
            }
            if (component.holderName == interceptor.component.holderName) {
                statement.add("%N", interceptor.component.fieldName)
            } else {
                statement.add("%N.%N", interceptor.component.holderName, interceptor.component.fieldName)
            }
        }
        statement.add(")")

        var rn = false
        for (dependency in component.dependencies) {
            if (dependency is ComponentDependency.AllOfDependency) {
                if (dependency.claim.claimType != DependencyClaim.DependencyClaimType.ALL_OF_PROMISE) {
                    val dependencyDeclarations = GraphResolutionHelper.findDependencyDeclarations(ctx, declarations, dependency.claim)
                    val dependencies = GraphResolutionHelper.findDependenciesForAllOf(ctx, dependency.claim, dependencyDeclarations, components)
                    for (d in dependencies) {
                        if (!rn) {
                            rn = true
                            statement.add(",\n")
                        } else {
                            statement.add(", ")
                        }
                        if (component.holderName == d.component!!.holderName) {
                            statement.add("%N", d.component!!.fieldName)
                        } else {
                            statement.add("%N.%N", d.component!!.holderName, d.component!!.fieldName)
                        }
                        if (dependency.claim.claimType == DependencyClaim.DependencyClaimType.ALL_OF_VALUE) {
                            statement.add(".valueOf()")
                        }
                    }
                }
                continue
            }
            if (dependency is ComponentDependency.PromiseOfDependency) {
                continue
            }
            if (dependency is ComponentDependency.PromisedProxyParameterDependency) {
                continue
            }
            if (dependency is ComponentDependency.SingleDependency && dependency.component != null) {
                if (!rn) {
                    rn = true
                    statement.add(",\n")
                } else {
                    statement.add(", ")
                }
                if (component.holderName == dependency.component!!.holderName) {
                    statement.add("%N", dependency.component!!.fieldName)
                } else {
                    statement.add("%N.%N", dependency.component!!.holderName, dependency.component!!.fieldName)
                }
                if (dependency is ComponentDependency.ValueOfDependency) {
                    statement.add(".valueOf()")
                }
            }
        }
        statement.unindent()
        statement.add("\n)")
        return statement.add("\n").build()
    }

    private fun getDependenciesCode(
        ctx: ProcessingContext,
        component: ResolvedComponent,
        declarations: ComponentDeclarations,
        components: ResolvedComponents
    ): CodeBlock {
        if (component.dependencies.isEmpty()) {
            return CodeBlock.of("")
        }
        val block = CodeBlock.builder().indent().add("\n")
        for ((i, dependency) in component.dependencies.withIndex()) {
            if (i > 0) {
                block.add(",\n")
            }
            block.add(dependency.write(ctx, declarations, components))
        }
        block.unindent().add("\n")
        return block.build()
    }
}
