package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
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
    private val codeGenerator: CodeGenerator,
) {
    private val readerTypeMetaParser = ReaderTypeMetaParser()
    private val writerTypeMetaParser = WriterTypeMetaParser()
    private val writerGenerator = JsonWriterGenerator()
    private val readerGenerator = JsonReaderGenerator()
    private val sealedReaderGenerator = SealedInterfaceReaderGenerator()
    private val sealedWriterGenerator = SealedInterfaceWriterGenerator()
    private val enumJsonReaderGenerator = EnumJsonReaderGenerator()
    private val enumJsonWriterGenerator = EnumJsonWriterGenerator()

    fun generateReader(jsonClassDeclaration: KSClassDeclaration) {
        val packageElement = jsonClassDeclaration.packageName.asString()
        val readerClassName = jsonClassDeclaration.jsonReaderName()
        val type = generateReader(ClassName(packageElement, readerClassName), jsonClassDeclaration)
        FileSpec.get(packageElement, type).writeTo(codeGenerator, false)
    }

    fun generateReader(target: ClassName, jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        return when {
            isSealed(jsonClassDeclaration) -> sealedReaderGenerator.generateSealedReader(target, jsonClassDeclaration)
            jsonClassDeclaration.modifiers.contains(Modifier.ENUM) -> enumJsonReaderGenerator.generateEnumReader(target, jsonClassDeclaration)
            else -> {
                val meta = readerTypeMetaParser.parse(jsonClassDeclaration)
                readerGenerator.generate(target, meta)
            }
        }
    }

    fun generateWriter(declaration: KSClassDeclaration) {
        val packageElement = declaration.packageName.asString()
        val writerClassName = declaration.jsonWriterName()
        val type = generateWriter(ClassName(packageElement, writerClassName), declaration)
        FileSpec.get(packageElement, type).writeTo(codeGenerator, false)
    }

    fun generateWriter(target: ClassName, declaration: KSClassDeclaration): TypeSpec {
        return when {
            isSealed(declaration) -> sealedWriterGenerator.generateSealedWriter(target, declaration)
            declaration.modifiers.contains(Modifier.ENUM) -> enumJsonWriterGenerator.generateEnumWriter(target, declaration)
            else -> {
                val meta = writerTypeMetaParser.parse(declaration)
                writerGenerator.generate(target, meta)
            }
        }
    }
}
