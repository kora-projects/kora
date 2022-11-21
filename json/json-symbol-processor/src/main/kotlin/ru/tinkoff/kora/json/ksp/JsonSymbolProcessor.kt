package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.common.annotation.JsonReader
import ru.tinkoff.kora.json.common.annotation.JsonWriter
import ru.tinkoff.kora.json.ksp.reader.JsonReaderGenerator
import ru.tinkoff.kora.json.ksp.reader.ReaderTypeMetaParser
import ru.tinkoff.kora.json.ksp.reader.SealedInterfaceReaderGenerator
import ru.tinkoff.kora.json.ksp.writer.JsonWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.SealedInterfaceWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.WriterTypeMetaParser
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor

@KspExperimental
class JsonSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(
            Json::class.qualifiedName!!,
            JsonReader::class.qualifiedName!!,
            JsonWriter::class.qualifiedName!!
        )
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val knownType = KnownType(resolver)
        val jsonReaderTypeMetaParser = ReaderTypeMetaParser(resolver, knownType, kspLogger)
        val jsonWriterTypeMetaParser = WriterTypeMetaParser(resolver,  kspLogger)
        val jsonReaderGenerator = JsonReaderGenerator(resolver)
        val jsonWriterGenerator = JsonWriterGenerator(resolver)
        val sealedInterfaceReaderGenerator = SealedInterfaceReaderGenerator(resolver, kspLogger)
        val sealedInterfaceWriterGenerator = SealedInterfaceWriterGenerator(resolver, kspLogger)
        val jsonProcessor = JsonProcessor(
            resolver,
            jsonReaderTypeMetaParser,
            jsonWriterTypeMetaParser,
            jsonWriterGenerator,
            jsonReaderGenerator,
            sealedInterfaceReaderGenerator,
            sealedInterfaceWriterGenerator,
            codeGenerator
        )
        val symbolsToProcess = getSupportedAnnotationTypes().map { resolver.getSymbolsWithAnnotation(it).toList() }.flatten().distinct()
        symbolsToProcess.forEach {
            when (it) {
                is KSClassDeclaration -> {
                    if (it.isAnnotationPresent(Json::class) || (it.isAnnotationPresent(JsonReader::class) && it.isAnnotationPresent(JsonWriter::class))) {
                        jsonProcessor.generateReader(it)
                        jsonProcessor.generateWriter(it)
                    } else if (it.isAnnotationPresent(JsonReader::class)) {
                        jsonProcessor.generateReader(it)
                    } else if (it.isAnnotationPresent(JsonWriter::class)) {
                        jsonProcessor.generateWriter(it)
                    }
                }
                is KSFunctionDeclaration -> {
                    if (it.isConstructor() && it.isAnnotationPresent(JsonReader::class)) {
                        jsonProcessor.generateReader(it.parentDeclaration!! as KSClassDeclaration)
                    }
                }
            }
        }
        return listOf()
    }
}

@KspExperimental
class JsonSymbolProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): JsonSymbolProcessor {
        return JsonSymbolProcessor(environment)
    }
}
