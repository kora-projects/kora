package io.koraframework.aop.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.Either
import io.koraframework.ksp.common.KoraSymbolProcessingEnv
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException
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
            } catch (e: Throwable) {
                throw IllegalStateException(
                    """
                    Kora internal error: failed to write generated AOP proxy '${typeSpec.name}' for '${declarationEntry.key}'.

                    This is not caused by the annotated class itself. Check that KSP can write to the generated sources directory and that no generated file is locked by another process.
                    """.trimIndent(),
                    e
                )
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
            classKind == ClassKind.CLASS && isAbstract() -> Either.right(ProcessingError(abstractClassError(this), this))
            classKind == ClassKind.CLASS && !isOpen() -> Either.right(ProcessingError(closedClassError(this), this))
            classKind == ClassKind.CLASS && findAopConstructor() == null -> Either.right(ProcessingError(missingConstructorError(this), this))
            classKind == ClassKind.CLASS -> Either.left(this)
            else -> Either.left(null)
        }

        is KSFunctionDeclaration -> when {
            !isOpen() -> Either.right(ProcessingError(closedFunctionError(this), this))
            parentDeclaration is KSClassDeclaration -> (parentDeclaration as KSClassDeclaration).findKsClassDeclaration()
            else -> Either.right(ProcessingError(topLevelFunctionError(this), this))
        }

        else -> Either.left(null)
    }

    private fun abstractClassError(declaration: KSClassDeclaration): String {
        return """
            AOP aspect cannot be applied to abstract class '${declaration.qualifiedName?.asString()}'.

            Fix: move the aspect annotation to a concrete open class or to an open member function that can be proxied.
        """.trimIndent()
    }

    private fun closedClassError(declaration: KSClassDeclaration): String {
        return """
            AOP aspect cannot be applied to class '${declaration.qualifiedName?.asString()}' because the class is not open.

            Fix: mark the class as open, or move the aspect annotation to an open member function.
            Example: open class ${declaration.simpleName.asString()}
        """.trimIndent()
    }

    private fun missingConstructorError(declaration: KSClassDeclaration): String {
        return """
            AOP proxy cannot be generated for '${declaration.qualifiedName?.asString()}': no suitable constructor was found.

            Fix: provide at least one public or protected constructor that can be called from the generated proxy.
        """.trimIndent()
    }

    private fun closedFunctionError(function: KSFunctionDeclaration): String {
        return """
            AOP aspect cannot be applied to function '${function.parentDeclaration}#${function.simpleName.asString()}' because the function is not open.

            Fix: mark the function as open, or apply the aspect to an open class.
            Example: open fun ${function.simpleName.asString()}(...)
        """.trimIndent()
    }

    private fun topLevelFunctionError(function: KSFunctionDeclaration): String {
        return """
            AOP aspect cannot be applied to top-level function '${function.simpleName.asString()}'.

            Fix: move the function into an open class and apply the aspect to an open member function.
        """.trimIndent()
    }
}


class AopSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return AopSymbolProcessor(environment)
    }
}
