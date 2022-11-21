package ru.tinkoff.kora.aop.symbol.processor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.common.AopAnnotation
import ru.tinkoff.kora.ksp.common.*
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.*

@KspExperimental
class AopSymbolProcessor(
    environment: SymbolProcessorEnvironment,
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val errors: MutableList<ProcessingError> = mutableListOf()
    private val classesToProcess = mutableMapOf<KSClassDeclaration, KSAnnotated>()
    private lateinit var aspects: List<KoraAspect>
    private lateinit var annotations: List<KSClassDeclaration>
    private lateinit var aopProcessor: AopProcessor

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val aspectsFactories = ServiceLoader.load(KoraAspectFactory::class.java, KoraAspectFactory::class.java.classLoader)
        KoraSymbolProcessingEnv.logger.logging("Aspect factories: ${aspectsFactories.joinToString { it.toString() }}")
        aspects = aspectsFactories
            .mapNotNull { it.create(resolver) }


        aopProcessor = AopProcessor(aspects, resolver)
        val aspectsNames = aspects
            .map { obj: KoraAspect -> obj.javaClass }
            .joinToString("\n\t", "\t", "") { obj -> obj.canonicalName }
        KoraSymbolProcessingEnv.logger.logging("Discovered aspects:\n$aspectsNames")
        annotations = aspects.asSequence().map { it.getSupportedAnnotationTypes() }.flatten().mapNotNull { resolver.getClassDeclarationByName(it) }.toList()

        val noAopAnnotation = annotations.filter { !it.isAnnotationPresent(AopAnnotation::class) }

        noAopAnnotation.forEach { noAop ->
            KoraSymbolProcessingEnv.logger.warn("Annotation ${noAop.simpleName.asString()} has no @AopAnnotation marker, it will not be handled by some util methods" )
        }

        val symbols = this.annotations
            .map { annotation -> resolver.getSymbolsWithAnnotation(annotation.qualifiedName!!.asString()).toList() }
            .flatten()

        val unableToProcess = symbols.filterNot { it.validate() }.toMutableList()
        symbols.forEach { symbol ->
            symbol.visitFunctionArgument { ksFunctionArgument ->
                findKsClassDeclaration(ksFunctionArgument)?.let {
                    classesToProcess.put(it, symbol)
                }
            }
            symbol.visitFunction { ksFunctionDeclaration ->
                findKsClassDeclaration(ksFunctionDeclaration)?.let {
                    classesToProcess.put(it, symbol)
                }
            }
            symbol.visitClass { declaration ->
                findKsClassDeclaration(declaration)?.let {
                    classesToProcess.put(it, symbol)
                }
            }
        }
        errors.forEach { error ->
            error.print(this.kspLogger)
        }
        if (errors.isNotEmpty()) {
            return symbols
        }

        classesToProcess.forEach { declarationEntry ->
            KoraSymbolProcessingEnv.logger.info("Processing type ${declarationEntry.key.qualifiedName!!.asString()} with aspects")
            val typeSpec: TypeSpec
            try {
                typeSpec = this.aopProcessor.applyAspects(declarationEntry.key)
            } catch (e: ProcessingErrorException) {
                e.printError(this.kspLogger)
                unableToProcess += declarationEntry.value
                return@forEach
            }
            val containingFile = declarationEntry.key.containingFile!!
            val packageName = containingFile.packageName.asString()
            val fileSpec = FileSpec.builder(
                packageName = packageName,
                fileName = typeSpec.name!!
            )
            try {
                fileSpec.addType(typeSpec).build().writeTo(codeGenerator = codeGenerator, aggregating = false)
            } catch (_: FileAlreadyExistsException) {
            }
        }

        return unableToProcess
    }

    private fun findKsClassDeclaration(declaration: KSAnnotated): KSClassDeclaration? {
        when (declaration) {
            is KSValueParameter -> {
                return when (val declarationParent = declaration.parent) {
                    is KSFunctionDeclaration -> {
                        findKsClassDeclaration(declarationParent)
                    }
                    is KSClassDeclaration -> {
                        findKsClassDeclaration(declarationParent)
                    }
                    else -> null
                }
            }
            is KSClassDeclaration -> {
                return when (declaration.classKind) {
                    ClassKind.INTERFACE -> {
                        null
                    }
                    ClassKind.CLASS -> {
                        if (declaration.isAbstract()) {
                            errors.add(ProcessingError("Aspects can not be applied to abstract classes, but $declaration is not abstract", declaration))
                            return null
                        }
                        if (!declaration.isOpen()) {
                            errors.add(ProcessingError("Aspects can be applied only to non final classes, but $declaration is not open", declaration))
                            return null
                        }
                        val constructor = findAopConstructor(declaration)
                        if (constructor == null) {
                            errors.add(ProcessingError("Can't find constructor suitable for aop proxy for $declaration", declaration))
                            return null
                        }
                        return declaration
                    }
                    else -> null
                }
            }
            is KSFunctionDeclaration -> {
                return if (!declaration.isOpen()) {
                    errors.add(ProcessingError("Aspects can be applied only to non final functions, but function ${declaration.parentDeclaration}#$declaration is not open", declaration))
                    return null
                } else if (declaration.parentDeclaration is KSClassDeclaration) {
                    findKsClassDeclaration(declaration.parentDeclaration as KSClassDeclaration)
                } else {
                    errors.add(ProcessingError("Can't apply aspects to top level function", declaration))
                    null
                }
            }
            else -> return null
        }
    }
}


@KspExperimental
class AopSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return AopSymbolProcessor(environment)
    }
}
