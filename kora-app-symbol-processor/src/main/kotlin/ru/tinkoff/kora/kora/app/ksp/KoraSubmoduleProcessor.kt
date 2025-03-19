package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessor.Companion.OPTION_SUBMODULE_GENERATION
import ru.tinkoff.kora.kora.app.ksp.KoraAppUtils.findSinglePublicConstructor
import ru.tinkoff.kora.kora.app.ksp.KoraAppUtils.validateComponent
import ru.tinkoff.kora.kora.app.ksp.KoraAppUtils.validateModule
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonAopUtils.hasAopAnnotations
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.makeTagAnnotationSpec
import ru.tinkoff.kora.ksp.common.parseTags

class KoraSubmoduleProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val isKoraAppSubmoduleEnabled = environment.options.getOrDefault(OPTION_SUBMODULE_GENERATION, "false").toBoolean()

    private val submodules = mutableSetOf<KSClassDeclaration>()
    private val annotatedModules = mutableListOf<KSClassDeclaration>()
    private val components = mutableSetOf<KSClassDeclaration>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()

        processModules(resolver).let { deferred.addAll(it) }
        processComponents(resolver).let { deferred.addAll(it) }
        processSubmodules(resolver).let { deferred.addAll(it) }

        return deferred
    }

    override fun finish() {
        for (submodule in submodules) {
            generateSubmodule(submodule)
        }
    }

    private fun processModules(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val moduleOfSymbols = resolver.getSymbolsWithAnnotation(CommonClassNames.module.canonicalName).toList()
        for (moduleSymbol in moduleOfSymbols) {
            if (moduleSymbol is KSClassDeclaration && moduleSymbol.classKind == ClassKind.INTERFACE) {
                if (moduleSymbol.validateModule()) {
                    annotatedModules.add(moduleSymbol)
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
                    if (componentSymbol.validateComponent()) {
                        components.add(componentSymbol)
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
            .forEach {
                if (submodules.none { a -> a.toClassName() == it.toClassName() }) {
                    if (it.validateModule()) {
                        submodules.add(it)
                    } else {
                        deferred.add(it)
                    }
                }
            }

        if (isKoraAppSubmoduleEnabled) {
            resolver.getSymbolsWithAnnotation(CommonClassNames.koraApp.canonicalName)
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.INTERFACE }
                .forEach {
                    if (submodules.none { a -> a.toClassName() == it.toClassName() }) {
                        if (it.validateModule()) {
                            submodules.add(it)
                        } else {
                            deferred.add(it)
                        }
                    }
                }
        }
        return deferred
    }

    private fun generateSubmodule(submodule: KSClassDeclaration) {
        val packageName = submodule.packageName.asString()
        val b = TypeSpec.interfaceBuilder(submodule.simpleName.asString() + "SubmoduleImpl")
            .generated(KoraAppProcessor::class)
        var componentCounter = 0
        for (component in components) {
            val constructor = component.findSinglePublicConstructor()
            val mb = FunSpec.builder("_component" + componentCounter++)
                .returns(component.toClassName())
            mb.addCode("return %T(", component.toClassName())
            for (i in constructor.parameters.indices) {
                val parameter = constructor.parameters[i]
                val tag = parameter.parseTags()
                val ps = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.toTypeName())
                if (tag.isNotEmpty()) {
                    ps.addAnnotation(tag.makeTagAnnotationSpec())
                }
                mb.addParameter(ps.build())
                if (i > 0) {
                    mb.addCode(", ")
                }
                mb.addCode("%N", parameter.name?.asString())
            }
            val tag = component.parseTags()
            if (tag.isNotEmpty()) {
                mb.addAnnotation(tag.makeTagAnnotationSpec())
            }
            if (component.findAnnotation(CommonClassNames.root) != null) {
                mb.addAnnotation(CommonClassNames.root)
            }
            mb.addCode(")\n")
            b.addFunction(mb.build())
        }
        val companion = TypeSpec.companionObjectBuilder()
            .generated(KoraAppProcessor::class)

        for ((moduleCounter, module) in annotatedModules.withIndex()) {
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
                    val tag = parameter.parseTags()
                    val ps = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.toTypeName())
                    if (tag.isNotEmpty()) {
                        ps.addAnnotation(tag.makeTagAnnotationSpec())
                    }
                    mb.addParameter(ps.build())
                    if (i > 0) {
                        mb.addCode(", ")
                    }
                    mb.addCode("%N", parameter.name?.asString())
                }
                val tag = component.parseTags()
                if (tag.isNotEmpty()) {
                    mb.addAnnotation(tag.makeTagAnnotationSpec())
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
