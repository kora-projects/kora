package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.json.ksp.reader.EnumJsonReaderGenerator
import ru.tinkoff.kora.json.ksp.reader.JsonReaderGenerator
import ru.tinkoff.kora.json.ksp.reader.ReaderTypeMetaParser
import ru.tinkoff.kora.json.ksp.reader.SealedInterfaceReaderGenerator
import ru.tinkoff.kora.json.ksp.writer.EnumJsonWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.JsonWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.SealedInterfaceWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.WriterTypeMetaParser

class JsonProcessor(
    private val resolver: Resolver,
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
    private val knownType: KnownType,
) {
    private val readerTypeMetaParser = ReaderTypeMetaParser(knownType, logger)
    private val writerTypeMetaParser = WriterTypeMetaParser(resolver)
    private val writerGenerator = JsonWriterGenerator(resolver)
    private val readerGenerator = JsonReaderGenerator(resolver)
    private val sealedReaderGenerator = SealedInterfaceReaderGenerator(resolver, logger)
    private val sealedWriterGenerator = SealedInterfaceWriterGenerator()
    private val enumJsonReaderGenerator = EnumJsonReaderGenerator()
    private val enumJsonWriterGenerator = EnumJsonWriterGenerator()

    fun generateReader(jsonClassDeclaration: KSClassDeclaration) {
        val packageElement = jsonClassPackage(jsonClassDeclaration)
        val readerClassName = jsonClassDeclaration.jsonReaderName()
        val readerDeclaration = resolver.getClassDeclarationByName("$packageElement.$readerClassName")
        if (readerDeclaration != null) {
            return
        }
        val readerType = when {
            isSealed(jsonClassDeclaration) -> sealedReaderGenerator.generateSealedReader(jsonClassDeclaration)
            jsonClassDeclaration.modifiers.contains(Modifier.ENUM) -> enumJsonReaderGenerator.generateEnumReader(jsonClassDeclaration)
            else -> {
                val meta = readerTypeMetaParser.parse(jsonClassDeclaration)
                readerGenerator.generate(meta)
            }
        }
        val fileSpec = FileSpec.builder(
            packageName = packageElement,
            fileName = readerType.name!!
        )
        fileSpec.addType(readerType)
        fileSpec.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }

    fun generateWriter(declaration: KSClassDeclaration) {
        val packageElement = jsonClassPackage(declaration)
        val writerClassName = declaration.jsonWriterName()
        val writerDeclaration = resolver.getClassDeclarationByName("$packageElement.$writerClassName")
        if (writerDeclaration != null) {
            return
        }
        val writerType = when {
            isSealed(declaration) -> sealedWriterGenerator.generateSealedWriter(declaration)
            declaration.modifiers.contains(Modifier.ENUM) -> enumJsonWriterGenerator.generateEnumWriter(declaration)
            else -> {
                val meta = writerTypeMetaParser.parse(declaration)
                writerGenerator.generate(meta)
            }
        }
        val fileSpec = FileSpec.builder(
            packageName = packageElement,
            fileName = writerType.name!!
        )
        fileSpec.addType(writerType)
        fileSpec.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }
}
