package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
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
        val symbolsToDelay = arrayListOf<KSAnnotated>()
        for (it in symbolsToProcess) {
            if (!it.validate()) {
                symbolsToDelay.add(it)
                continue
            }
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
                        } else if (it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation)) {
                            val enclosingEnum = enclosingEnum(it)
                            if (enclosingEnum == null) {
                                kspLogger.error(
                                    "@JsonReader on a method is supported only for an enum factory method (in the companion object of an enum)",
                                    it
                                )
                            } else if (processedReaders.add(enclosingEnum.qualifiedName!!.asString())) {
                                jsonProcessor.generateReader(enclosingEnum)
                            }
                        }
                        if (it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation)) {
                            val enclosingEnum = enclosingEnum(it)
                            if (enclosingEnum == null) {
                                kspLogger.error(
                                    "@JsonWriter on a method is supported only for an enum method (in the companion object of an enum)",
                                    it
                                )
                            } else if (processedWriters.add(enclosingEnum.qualifiedName!!.asString())) {
                                jsonProcessor.generateWriter(enclosingEnum)
                            }
                        }
                    }
                }
            } catch (e: ProcessingErrorException) {
                e.printError(kspLogger)
            }
        }
        return symbolsToDelay
    }

    private fun enclosingEnum(function: KSFunctionDeclaration): KSClassDeclaration? {
        val parent = function.parentDeclaration as? KSClassDeclaration ?: return null
        val enum = if (parent.isCompanionObject) parent.parentDeclaration as? KSClassDeclaration else parent
        return if (enum != null && enum.classKind == ClassKind.ENUM_CLASS) enum else null
    }
}

class JsonSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): JsonSymbolProcessor {
        return JsonSymbolProcessor(environment)
    }
}
