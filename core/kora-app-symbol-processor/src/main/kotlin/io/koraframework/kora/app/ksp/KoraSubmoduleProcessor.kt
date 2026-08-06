package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.kora.app.ksp.KoraAppUtils.findSinglePublicConstructor
import io.koraframework.kora.app.ksp.KoraAppUtils.validateComponent
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonAopUtils.hasAopAnnotations
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils.parseTag
import io.koraframework.ksp.common.TagUtils.toTagAnnotation

class KoraSubmoduleProcessor(val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    companion object {
        const val OPTION_SUBMODULE_GENERATION = "kora.app.submodule.enabled"
    }

    private val isKoraAppSubmoduleEnabled = environment.options.getOrDefault(OPTION_SUBMODULE_GENERATION, "false").toBoolean()

    // A KSP symbol is invalidated once a later round regenerates the file it came from, so the
    // declarations collected here are kept as names and resolved again against the last round's
    // resolver in finish(). Holding the KSClassDeclaration itself made every access throw
    // KaInvalidLifetimeOwnerAccessException as soon as any other processor generated code.
    private val submodules = mutableSetOf<String>()
    private val annotatedModules = mutableListOf<String>()
    private val components = mutableSetOf<String>()
    private var lastResolver: Resolver? = null

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        lastResolver = resolver

        processModules(resolver).let { deferred.addAll(it) }
        processComponents(resolver).let { deferred.addAll(it) }
        processSubmodules(resolver).let { deferred.addAll(it) }

        return deferred
    }

    override fun finish() {
        val resolver = lastResolver ?: return
        for (submodule in submodules) {
            val declaration = resolver.classDeclaration(submodule) ?: continue
            generateSubmodule(resolver, declaration)
        }
    }

    private fun Resolver.classDeclaration(qualifiedName: String): KSClassDeclaration? =
        getClassDeclarationByName(getKSNameFromString(qualifiedName))

    private fun processModules(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val moduleOfSymbols = resolver.getSymbolsWithAnnotation(CommonClassNames.module.canonicalName).toList()
        for (moduleSymbol in moduleOfSymbols) {
            if (moduleSymbol is KSClassDeclaration && moduleSymbol.classKind == ClassKind.INTERFACE) {
                if (moduleSymbol.validateAll()) {
                    moduleSymbol.qualifiedName?.asString()?.let { annotatedModules.add(it) }
                } else {
                    deferred.add(moduleSymbol)
                }
            }
        }
        return deferred
    }

    private fun processComponents(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val componentSymbols = resolver.getSymbolsWithAnnotation(CommonClassNames.component.canonicalName).toList()
        for (componentSymbol in componentSymbols) {
            if (componentSymbol is KSClassDeclaration && componentSymbol.classKind == ClassKind.CLASS) {
                if (!componentSymbol.modifiers.contains(Modifier.ABSTRACT) && !hasAopAnnotations(componentSymbol)) {
                    if (componentSymbol.validateAll() && componentSymbol.validateComponent()) {
                        componentSymbol.qualifiedName?.asString()?.let { components.add(it) }
                    } else {
                        deferred.add(componentSymbol)
                    }
                }
            }
        }
        return deferred
    }

    private fun processSubmodules(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()

        resolver.getSymbolsWithAnnotation(CommonClassNames.koraSubmodule.canonicalName)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE }
            .forEach { collectSubmodule(it, deferred) }

        if (isKoraAppSubmoduleEnabled) {
            resolver.getSymbolsWithAnnotation(CommonClassNames.koraApp.canonicalName)
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.INTERFACE }
                .forEach { collectSubmodule(it, deferred) }
        }
        return deferred
    }

    private fun collectSubmodule(declaration: KSClassDeclaration, deferred: MutableList<KSAnnotated>) {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return
        if (submodules.contains(qualifiedName)) {
            return
        }
        if (declaration.validateAll()) {
            submodules.add(qualifiedName)
        } else {
            deferred.add(declaration)
        }
    }

    private fun generateSubmodule(resolver: Resolver, submodule: KSClassDeclaration) {
        val packageName = submodule.packageName.asString()
        val b = TypeSpec.interfaceBuilder(submodule.simpleName.asString() + "SubmoduleImpl")
            .generated(KoraSubmoduleProcessor::class)
        var componentCounter = 0
        for (componentName in components) {
            val component = resolver.classDeclaration(componentName) ?: continue
            val constructor = component.findSinglePublicConstructor()
            val mb = FunSpec.builder("_component" + componentCounter++)
                .returns(component.toClassName())
            mb.addCode("return %T(", component.toClassName())
            for (i in constructor.parameters.indices) {
                val parameter = constructor.parameters[i]
                val tag = parameter.parseTag()
                val ps = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.toTypeName())
                if (tag != null) {
                    ps.addAnnotation(tag.toTagAnnotation())
                }
                mb.addParameter(ps.build())
                if (i > 0) {
                    mb.addCode(", ")
                }
                mb.addCode("%N", parameter.name?.asString())
            }
            val tag = component.parseTag()
            if (tag != null) {
                mb.addAnnotation(tag.toTagAnnotation())
            }
            if (component.findAnnotation(CommonClassNames.root) != null) {
                mb.addAnnotation(CommonClassNames.root)
            }
            mb.addCode(")\n")
            b.addFunction(mb.build())
        }
        val companion = TypeSpec.companionObjectBuilder()
            .generated(KoraAppProcessor::class)

        for ((moduleCounter, moduleClassName) in annotatedModules.withIndex()) {
            val module = resolver.classDeclaration(moduleClassName) ?: continue
            val moduleName = "_module$moduleCounter"
            val type = module.toClassName()
            companion.addProperty(PropertySpec.builder(moduleName, type).initializer("object : %T {}", type).build())
            for (component in module.getDeclaredFunctions()) {
                val componentType = component.returnType!!.toTypeName()
                val mb = FunSpec.builder("_component" + componentCounter++)
                    .returns(componentType)
                mb.addCode("return %N.%N(", moduleName, component.simpleName.asString())
                for (i in component.parameters.indices) {
                    val parameter = component.parameters[i]
                    val tag = parameter.parseTag()
                    val ps = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.toTypeName())
                    if (tag != null) {
                        ps.addAnnotation(tag.toTagAnnotation())
                    }
                    mb.addParameter(ps.build())
                    if (i > 0) {
                        mb.addCode(", ")
                    }
                    mb.addCode("%N", parameter.name?.asString())
                }
                val tag = component.parseTag()
                if (tag != null) {
                    mb.addAnnotation(tag.toTagAnnotation())
                }
                if (component.findAnnotation(CommonClassNames.defaultComponent) != null) {
                    mb.addAnnotation(CommonClassNames.defaultComponent)
                }
                if (component.findAnnotation(CommonClassNames.root) != null) {
                    mb.addAnnotation(CommonClassNames.root)
                }
                mb.addCode(")\n")
                b.addFunction(mb.build())
            }
        }
        val typeSpec = b.addType(companion.build()).build()
        val fileSpec = FileSpec.builder(packageName, typeSpec.name!!).addType(typeSpec).build()
        fileSpec.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
    }
}
