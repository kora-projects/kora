package io.koraframework.json.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.generatedHolder
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

    private class JsonTypes(val declaration: KSClassDeclaration) {
        var reader: TypeSpec? = null
        var writer: TypeSpec? = null
    }

    // reader and writer of a type are written into one file, so everything requested in the round is collected first
    private val requested = LinkedHashMap<String, JsonTypes>()

    private fun requested(declaration: KSClassDeclaration) = requested.getOrPut(declaration.qualifiedName!!.asString()) { JsonTypes(declaration) }

    fun generateReader(jsonClassDeclaration: KSClassDeclaration) {
        requested(jsonClassDeclaration).reader = when {
            isSealed(jsonClassDeclaration) -> sealedReaderGenerator.generateSealedReader(jsonClassDeclaration)
            jsonClassDeclaration.modifiers.contains(Modifier.ENUM) -> enumJsonReaderGenerator.generateEnumReader(jsonClassDeclaration)
            delegatingReaderGenerator.detectReaderFactory(jsonClassDeclaration) != null -> delegatingReaderGenerator.generate(jsonClassDeclaration)
            else -> {
                val meta = readerTypeMetaParser.parse(jsonClassDeclaration)
                readerGenerator.generate(meta)
            }
        }
    }

    fun generateWriter(declaration: KSClassDeclaration) {
        requested(declaration).writer = when {
            isSealed(declaration) -> sealedWriterGenerator.generateSealedWriter(declaration)
            declaration.modifiers.contains(Modifier.ENUM) -> enumJsonWriterGenerator.generateEnumWriter(declaration)
            delegatingWriterGenerator.detectWriterMethod(declaration) != null -> delegatingWriterGenerator.generate(declaration)
            else -> {
                val meta = writerTypeMetaParser.parse(declaration)
                writerGenerator.generate(meta)
            }
        }
    }

    /**
     * Writes everything requested in the round.
     *
     * @param writtenHolders holders written in the previous rounds: holder file can't be written twice, so reader or writer
     * requested for such type later is written as a top level class with the name used before holders were introduced
     */
    fun write(writtenHolders: MutableSet<String>) {
        for (types in requested.values) {
            val declaration = types.declaration
            val packageName = jsonClassPackage(declaration)
            val holderName = declaration.jsonHolderName()
            val holderQualifiedName = "$packageName.$holderName"
            if (holderQualifiedName in writtenHolders) {
                types.reader?.let { writeTopLevel(packageName, it.toBuilder(name = declaration.jsonReaderName()).build()) }
                types.writer?.let { writeTopLevel(packageName, it.toBuilder(name = declaration.jsonWriterName()).build()) }
                continue
            }
            if (resolver.getClassDeclarationByName(holderQualifiedName) != null) {
                continue
            }
            val holder = generatedHolder(holderName, JsonSymbolProcessor::class)
            types.reader?.let { holder.addType(it) }
            types.writer?.let { holder.addType(it) }
            writeTopLevel(packageName, holder.build())
            writtenHolders.add(holderQualifiedName)
        }
        requested.clear()
    }

    private fun writeTopLevel(packageName: String, type: TypeSpec) {
        FileSpec.builder(packageName = packageName, fileName = type.name!!)
            .addType(type)
            .build()
            .writeTo(codeGenerator = codeGenerator, aggregating = false)
    }
}
