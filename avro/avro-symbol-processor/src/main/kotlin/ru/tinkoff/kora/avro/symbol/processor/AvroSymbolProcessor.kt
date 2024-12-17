package ru.tinkoff.kora.avro.symbol.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import ru.tinkoff.kora.avro.symbol.processor.reader.AvroReaderGenerator
import ru.tinkoff.kora.avro.symbol.processor.writer.AvroWriterGenerator
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

class AvroSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val processedReaders = HashSet<String>()
    private val processedWriters = HashSet<String>()
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    private fun getSupportedAnnotationTypes() = setOf(
        AvroTypes.avroBinary.canonicalName,
        AvroTypes.avroJson.canonicalName,
    )

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbolsToProcess = getSupportedAnnotationTypes()
            .map { resolver.getSymbolsWithAnnotation(it).toList() }
            .flatten()
            .distinct()

        val readerGenerator = AvroReaderGenerator(resolver, codeGenerator)
        val writerGenerator = AvroWriterGenerator(resolver, codeGenerator)
        for (it in symbolsToProcess) {
            try {
                when (it) {
                    is KSClassDeclaration -> {
                        if (it.isAnnotationPresent(AvroTypes.avroBinary)) {
                            if (processedReaders.add(it.qualifiedName!!.asString())) {
                                readerGenerator.generateBinary(it)
                                writerGenerator.generateBinary(it)
                            }
                        } else if (it.isAnnotationPresent(AvroTypes.avroJson)) {
                            if (processedWriters.add(it.qualifiedName!!.asString())) {
                                readerGenerator.generateJson(it)
                                writerGenerator.generateJson(it)
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

