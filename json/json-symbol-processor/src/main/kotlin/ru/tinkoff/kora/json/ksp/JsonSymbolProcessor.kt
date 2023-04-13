package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

class JsonSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val processedReaders = HashSet<String>()
    private val processedWriters = HashSet<String>()
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private fun getSupportedAnnotationTypes() = setOf(
        JsonTypes.json.canonicalName,
        JsonTypes.jsonReaderAnnotation.canonicalName,
        JsonTypes.jsonWriterAnnotation.canonicalName,
    )

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val knownType = KnownType(resolver)
        val jsonProcessor = JsonProcessor(
            resolver,
            kspLogger,
            codeGenerator,
            knownType
        )
        val symbolsToProcess = getSupportedAnnotationTypes().map { resolver.getSymbolsWithAnnotation(it).toList() }.flatten().distinct()
        for (it in symbolsToProcess) {
            try {
                when (it) {
                    is KSClassDeclaration -> {

                        if (it.isAnnotationPresent(JsonTypes.json) || (it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation) && it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation))) {
                            if (processedReaders.add(it.qualifiedName!!.asString())) {
                                jsonProcessor.generateReader(it)
                            }
                            if (processedWriters.add(it.qualifiedName!!.asString())) {
                                jsonProcessor.generateWriter(it)
                            }
                        } else if (it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation)) {
                            if (processedReaders.add(it.qualifiedName!!.asString())) {
                                jsonProcessor.generateReader(it)
                            }
                        } else if (it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation)) {
                            if (processedWriters.add(it.qualifiedName!!.asString())) {
                                jsonProcessor.generateWriter(it)
                            }
                        }
                    }

                    is KSFunctionDeclaration -> {
                        if (it.isConstructor() && it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation)) {
                            val clazz = it.parentDeclaration!! as KSClassDeclaration
                            if (processedReaders.add(clazz.qualifiedName!!.asString())) {
                                jsonProcessor.generateReader(clazz)
                            }
                        }
                    }
                }
            } catch (e: ProcessingErrorException) {
                e.printError(kspLogger)
            }
        }
        return listOf()
    }
}

class JsonSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): JsonSymbolProcessor {
        return JsonSymbolProcessor(environment)
    }
}
