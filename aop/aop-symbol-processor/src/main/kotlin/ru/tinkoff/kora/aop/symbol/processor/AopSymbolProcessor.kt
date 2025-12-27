package ru.tinkoff.kora.aop.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.Either
import ru.tinkoff.kora.ksp.common.KoraSymbolProcessingEnv
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.*

class AopSymbolProcessor(
    environment: SymbolProcessorEnvironment,
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val aspectsFactories = ServiceLoader.load(KoraAspectFactory::class.java, KoraAspectFactory::class.java.classLoader)
        val aspects = aspectsFactories
            .mapNotNull { it.create(resolver) }
        val aopProcessor = AopProcessor(aspects, resolver)
        val annotations = aspects.asSequence()
            .map { it.getSupportedAnnotationTypes() }
            .flatten()
            .mapNotNull { resolver.getClassDeclarationByName(it) }
            .toList()

        annotations
            .filter { !it.isAnnotationPresent(CommonClassNames.aopAnnotation) }
            .forEach { KoraSymbolProcessingEnv.logger.warn("Annotation ${it.simpleName.asString()} has no @AopAnnotation marker, it will not be handled by some util methods") }

        val deferred = mutableListOf<KSAnnotated>()
        val errors = mutableListOf<ProcessingError>()
        val symbolsToProcess = mutableMapOf<String, KSClassDeclaration>()

        for (annotation in annotations) {
            val symbols = resolver.getSymbolsWithAnnotation(annotation.qualifiedName!!.asString())
            for (symbol in symbols) {
                when (val classDeclaration = symbol.findKsClassDeclaration()) {
                    is Either.Left -> classDeclaration.value.let {
                        when {
                            it == null -> {}
                            it.validateAll() -> symbolsToProcess[it.qualifiedName!!.asString()] = it
                            else -> deferred.add(symbol)
                        }
                    }

                    is Either.Right -> errors.add(classDeclaration.value)
                }
            }
        }

        errors.forEach { error ->
            error.print(this.kspLogger)
        }

        for (declarationEntry in symbolsToProcess) {
            KoraSymbolProcessingEnv.logger.info("Processing type ${declarationEntry.key} with aspects", declarationEntry.value)
            val typeSpec: TypeSpec
            try {
                typeSpec = aopProcessor.applyAspects(declarationEntry.value)
            } catch (e: ProcessingErrorException) {
                e.printError(this.kspLogger)
                continue
            }
            val containingFile = declarationEntry.value.containingFile!!
            val packageName = containingFile.packageName.asString()
            val fileSpec = FileSpec.builder(
                packageName = packageName,
                fileName = typeSpec.name!!
            )
            try {
                fileSpec.addType(typeSpec).build().writeTo(codeGenerator, false)
            } catch (_: FileAlreadyExistsException) {
            }
        }

        return deferred
    }

    private fun KSAnnotated.findKsClassDeclaration(): Either<KSClassDeclaration?, ProcessingError> = when (this) {
        is KSValueParameter -> when (val declarationParent = this.parent) {
            is KSFunctionDeclaration -> declarationParent.findKsClassDeclaration()
            is KSClassDeclaration -> declarationParent.findKsClassDeclaration()
            else -> Either.left(null)
        }

        is KSClassDeclaration -> when {
            classKind == ClassKind.CLASS && isAbstract() -> Either.right(ProcessingError("Aspects can't be applied to abstract classes, but $this is abstract", this))
            classKind == ClassKind.CLASS && !isOpen() -> Either.right(ProcessingError("Aspects can be applied only to open classes, but $this is not open", this))
            classKind == ClassKind.CLASS && findAopConstructor() == null -> Either.right(ProcessingError("Can't find constructor suitable for aop proxy for $this", this))
            classKind == ClassKind.CLASS -> Either.left(this)
            else -> Either.left(null)
        }

        is KSFunctionDeclaration -> when {
            !isOpen() -> Either.right(ProcessingError("Aspects applied only to open functions, but function ${parentDeclaration}#$this is not open", this))
            parentDeclaration is KSClassDeclaration -> (parentDeclaration as KSClassDeclaration).findKsClassDeclaration()
            else -> Either.right(ProcessingError("Can't apply aspects to top level function", this))
        }

        else -> Either.left(null)
    }
}


class AopSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return AopSymbolProcessor(environment)
    }
}
