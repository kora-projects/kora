package io.koraframework.json.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.json.ksp.reader.DelegatingJsonReaderGenerator
import io.koraframework.json.ksp.reader.EnumJsonReaderGenerator
import io.koraframework.json.ksp.reader.JsonReaderGenerator
import io.koraframework.json.ksp.reader.ReaderTypeMetaParser
import io.koraframework.json.ksp.reader.SealedInterfaceReaderGenerator
import io.koraframework.json.ksp.writer.DelegatingJsonWriterGenerator
import io.koraframework.json.ksp.writer.EnumJsonWriterGenerator
import io.koraframework.json.ksp.writer.JsonWriterGenerator
import io.koraframework.json.ksp.writer.SealedInterfaceWriterGenerator
import io.koraframework.json.ksp.writer.WriterTypeMetaParser

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
    private val sealedReaderGenerator = SealedInterfaceReaderGenerator()
    private val sealedWriterGenerator = SealedInterfaceWriterGenerator()
    private val enumJsonReaderGenerator = EnumJsonReaderGenerator()
    private val enumJsonWriterGenerator = EnumJsonWriterGenerator()
    private val delegatingReaderGenerator = DelegatingJsonReaderGenerator()
    private val delegatingWriterGenerator = DelegatingJsonWriterGenerator()

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
            delegatingReaderGenerator.detectReaderFactory(jsonClassDeclaration) != null -> delegatingReaderGenerator.generate(jsonClassDeclaration)
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
            delegatingWriterGenerator.detectWriterMethod(declaration) != null -> delegatingWriterGenerator.generate(declaration)
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
