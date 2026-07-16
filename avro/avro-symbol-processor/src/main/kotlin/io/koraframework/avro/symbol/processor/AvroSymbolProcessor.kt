package io.koraframework.avro.symbol.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.avro.symbol.processor.reader.AvroReaderGenerator
import io.koraframework.avro.symbol.processor.writer.AvroWriterGenerator

class AvroSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val processed = HashSet<String>()
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    private fun getSupportedAnnotationTypes() = setOf(
        AvroTypes.avro.canonicalName,
    )

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbolsToProcess = getSupportedAnnotationTypes()
            .flatMap { resolver.getSymbolsWithAnnotation(it).toList() }
            .distinct()

        val readerGenerator = AvroReaderGenerator(resolver, codeGenerator)
        val writerGenerator = AvroWriterGenerator(resolver, codeGenerator)
        for (it in symbolsToProcess) {
            try {
                when (it) {
                    is KSClassDeclaration -> {
                        if (it.isAnnotationPresent(AvroTypes.avro)) {
                            generate(it, readerGenerator, writerGenerator)
                        }
                    }
                }
            } catch (e: ProcessingErrorException) {
                e.printError(kspLogger)
            }
        }
        return listOf()
    }

    private fun generate(
        declaration: KSClassDeclaration,
        readerGenerator: AvroReaderGenerator,
        writerGenerator: AvroWriterGenerator
    ) {
        if (processed.add(declaration.qualifiedName!!.asString())) {
            readerGenerator.generate(declaration)
            writerGenerator.generate(declaration)
        }
    }
}
