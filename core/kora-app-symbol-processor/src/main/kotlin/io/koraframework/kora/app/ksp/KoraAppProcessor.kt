package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.kora.app.ksp.KoraAppUtils.validateComponent
import io.koraframework.kora.app.ksp.component.ResolvedComponent
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.kora.app.ksp.declaration.ModuleDeclaration
import io.koraframework.kora.app.ksp.exception.UnresolvedDependencyException
import io.koraframework.kora.app.ksp.interceptor.ComponentInterceptors
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonAopUtils.hasAopAnnotations
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.CommonClassNames.isVoid
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils
import io.koraframework.ksp.common.exception.ProcessingErrorException

class KoraAppProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    companion object {
        const val COMPONENTS_PER_HOLDER_CLASS = 500
    }


    private val codeGenerator = environment.codeGenerator
    private val annotatedInterfaceModules = mutableListOf<String>()
    private val annotatedClassModules = mutableListOf<String>() // @Disabled("Haven't decided whether to release it yet")
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
                write(ctx, element, graph.allModules, graph.components, graph.conditionByTag)
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
                kspLogger.error(
                    """
                    @KoraApp can only be applied to interfaces.

                    Fix:
                      - Change this type to an interface.
                      - Move @KoraApp to an interface that declares root components and modules.
                    """.trimIndent(),
                    declaration
                )
            }
        }
        hasDeferred = deferred.isNotEmpty()
        return deferred
    }

    private fun buildGraph(ctx: ProcessingContext, declaration: KSClassDeclaration): ResolvedGraph {
        if (declaration.classKind != ClassKind.INTERFACE) {
            throw ProcessingErrorException(
                """
                @KoraApp can only be applied to interfaces.

                Fix:
                  - Change this type to an interface.
                  - Move @KoraApp to an interface that declares root components and modules.
                """.trimIndent(),
                declaration
            )
        }
        val rootErasure = declaration.asStarProjectedType()
        val rootModule = ModuleDeclaration.MixedInModule(declaration)
        val filterObjectMethods: (KSFunctionDeclaration) -> Boolean = {
            val name = it.simpleName.asString()
            !it.modifiers.contains(Modifier.PRIVATE)
                && name != "<init>"
                && name != "equals"
                && name != "hashCode"
                && name != "toString"
                && it.returnType != null && !it.returnType!!.isVoid() // todo find out a better way to filter object methods
        }
        val mixedInComponents = declaration.getAllFunctions()
            .filter(filterObjectMethods)
            .toMutableList()

        val allInterfaces = declaration.getAllSuperTypes().toList()
        val submodules = findKoraSubmoduleModules(ctx.resolver, allInterfaces, declaration)
        val allModules = (submodules + annotatedInterfaceModules.map { resolver!!.getClassDeclarationByName(it)!! })
            .flatMap { it.getAllSuperTypes().map { it.declaration as KSClassDeclaration } + it }
            .filter { it.qualifiedName?.asString() != "kotlin.Any" }
            .toSet()
            .toList()

        val annotatedModules = allModules
            .filter { !it.asStarProjectedType().isAssignableFrom(rootErasure) }
            .map { ModuleDeclaration.AnnotatedModule(it) }
        val annotatedModuleComponentsTmp = mutableListOf<ComponentDeclaration.FromModuleComponent>()
        val factoryModuleComponents = mutableListOf<ComponentDeclaration.FromModuleComponent>()
        for (module in annotatedModules) {
            for (func in module.element.getDeclaredFunctions().filter(filterObjectMethods)) {
                annotatedModuleComponentsTmp.add(ComponentDeclaration.fromModule(ctx, module, func))
                if (func.isAnnotationPresent(CommonClassNames.factoryModule)) {
                    val returnTypeDecl = func.returnType?.resolve()?.declaration
                    if (returnTypeDecl !is KSClassDeclaration) {
                        throw ProcessingErrorException(
                            """
                            @FactoryModule function must return a class or interface type.

                            Fix:
                              - Change the return type to a module class/interface.
                              - Remove @FactoryModule if this function is a regular provider.
                            """.trimIndent(),
                            func
                        )
                    }
                    val methodTag = TagUtils.parseTagValue(func)
                    val methodModule = ModuleDeclaration.FactoryModule(returnTypeDecl, methodTag)
                    returnTypeDecl.getAllFunctions()
                        .filter(filterObjectMethods)
                        .forEach { innerFunc -> factoryModuleComponents.add(ComponentDeclaration.fromModule(ctx, methodModule, innerFunc)) }
                }
            }
        }
        val annotatedModuleComponents = ArrayList(annotatedModuleComponentsTmp)
        for (annotatedComponent in annotatedModuleComponentsTmp) {
            if (annotatedComponent.method.modifiers.contains(Modifier.OVERRIDE)) {
                val overridee = annotatedComponent.method.findOverridee()
                annotatedModuleComponents.removeIf { it.method == overridee }
                mixedInComponents.remove(overridee)
            }
        }
        annotatedModuleComponents.addAll(factoryModuleComponents)
        val allComponents = ArrayList<ComponentDeclaration>(annotatedModuleComponents.size + mixedInComponents.size + 200)
        for (componentClass in components) {
            val decl = ctx.resolver.getClassDeclarationByName(componentClass)!!
            allComponents.add(ComponentDeclaration.fromAnnotated(ctx, decl))
        }
        for (func in mixedInComponents) {
            allComponents.add(ComponentDeclaration.fromModule(ctx, rootModule, func))
            if (func.isAnnotationPresent(CommonClassNames.factoryModule)) {
                val returnTypeDecl = func.returnType?.resolve()?.declaration as? KSClassDeclaration ?: continue
                val methodTag = TagUtils.parseTagValue(func)
                val methodModule = ModuleDeclaration.FactoryModule(returnTypeDecl, methodTag)
                returnTypeDecl.getAllFunctions()
                    .filter(filterObjectMethods)
                    .forEach { innerFunc -> allComponents.add(ComponentDeclaration.fromModule(ctx, methodModule, innerFunc)) }
            }
        }
        allComponents.addAll(annotatedModuleComponents)
        for (factoryModule in this.annotatedClassModules) {
            val factoryModuleDecl = ModuleDeclaration.ClassModule(resolver!!.getClassDeclarationByName(factoryModule)!!)
            allComponents.add(ComponentDeclaration.fromAnnotated(ctx, factoryModuleDecl.element))
            factoryModuleDecl.element.getAllFunctions()
                .filter(filterObjectMethods)
                .forEach { innerFunc -> allComponents.add(ComponentDeclaration.fromModule(ctx, factoryModuleDecl, innerFunc)) }
        }
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
        val b = GraphBuilder(ctx, declaration, allModules, components, templateComponents)
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
                    resolver.getClassDeclarationByName(ksName) ?: throw ProcessingErrorException(
                        """
                        Module declaration cannot be resolved:
                          type: ${ksName.asString()}

                        Fix:
                          - Check imports and module dependencies.
                          - Compile again after fixing earlier compiler errors.
                        """.trimIndent(),
                        it
                    )
                }
            }
            .toList()
    }

    private fun processModules(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val moduleOfSymbols = resolver.getSymbolsWithAnnotation(CommonClassNames.module.canonicalName)
        for (module in moduleOfSymbols) {
            if (module is KSClassDeclaration) {
                if (module.classKind == ClassKind.INTERFACE) {
                    if (module.validateAll()) {
                        annotatedInterfaceModules.add(module.qualifiedName!!.asString())
                    } else {
                        deferred.add(module)
                    }
//                @Disabled("Haven't decided whether to release it yet")
//                } else if (module.classKind == ClassKind.CLASS) {
//                    if (module.validateAll()) {
//                        annotatedClassModules.add(module.qualifiedName!!.asString())
//                    } else {
//                        deferred.add(module)
//                    }
//                }
                } else {
                    kspLogger.error(
                        """
                        @Module can only be applied to interfaces.

                        Fix:
                          - Change this type to an interface.
                          - Move module factory methods to an interface annotated with @Module.
                        """.trimIndent(),
                        module
                    )
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
        components: List<ResolvedComponent>,
        conditionByTag: MutableMap<ClassName, ResolvedComponent>
    ) {
        val interceptors: ComponentInterceptors = ComponentInterceptors.parseInterceptors(ctx, components)
        kspLogger.logging("Found interceptors: $interceptors")
        val applicationImplFile = this.generateImpl(declaration, allModules)
        val applicationGraphFile = GraphFileGenerator(ctx, declaration, allModules, interceptors, components, conditionByTag)
            .generate()
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

}
