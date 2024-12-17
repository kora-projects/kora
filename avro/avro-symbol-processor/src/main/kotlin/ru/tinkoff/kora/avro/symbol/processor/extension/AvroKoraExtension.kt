package ru.tinkoff.kora.avro.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import ru.tinkoff.kora.avro.symbol.processor.*
import ru.tinkoff.kora.avro.symbol.processor.reader.AvroReaderGenerator
import ru.tinkoff.kora.avro.symbol.processor.writer.AvroWriterGenerator
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

class AvroKoraExtension(
    private val resolver: Resolver,
    private val kspLogger: KSPLogger,
    codeGenerator: CodeGenerator
) : KoraExtension {
    private val writerErasure = resolver.getClassDeclarationByName(AvroTypes.writer.canonicalName)!!.asStarProjectedType()
    private val readerErasure = resolver.getClassDeclarationByName(AvroTypes.reader.canonicalName)!!.asStarProjectedType()
    private val readerGenerator = AvroReaderGenerator(resolver, codeGenerator)
    private val writerGenerator = AvroWriterGenerator(resolver, codeGenerator)

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        val isBinary = tags.isEmpty() || isBinary(tags)
        val isJson = isJson(tags)
        if (!isBinary && !isJson) {
            return null
        }

        val actualType = type.makeNotNullable()
        val erasure = actualType.starProjection()
        if (erasure == writerErasure) {
            val possibleClass = type.arguments[0].type!!.resolve()
            val possibleClassDeclaration = possibleClass.declaration
            if (possibleClassDeclaration !is KSClassDeclaration
                || possibleClassDeclaration.modifiers.contains(Modifier.ENUM)
                || possibleClassDeclaration.modifiers.contains(Modifier.SEALED)
            ) {
                return null
            }

            if (isBinary && possibleClassDeclaration.isAnnotationPresent(AvroTypes.avroBinary)) {
                return generatedByProcessor(resolver, possibleClassDeclaration, "AvroBinaryWriter")
            }
            if (isJson && possibleClassDeclaration.isAnnotationPresent(AvroTypes.avroJson)) {
                return generatedByProcessor(resolver, possibleClassDeclaration, "AvroJsonWriter")
            }

            try {
                return { generateWriter(resolver, possibleClassDeclaration, isBinary, tags) }
            } catch (e: ProcessingErrorException) {
                e.message?.let { kspLogger.warn(it, null) }
                return null
            }
        }

        if (erasure == readerErasure) {
            val possibleClass = type.arguments[0].type!!.resolve()
            val possibleClassDeclaration = possibleClass.declaration
            if (possibleClassDeclaration !is KSClassDeclaration
                || possibleClassDeclaration.modifiers.contains(Modifier.ENUM)
                || possibleClassDeclaration.modifiers.contains(Modifier.SEALED)
            ) {
                return null
            }

            if (isBinary && possibleClassDeclaration.isAnnotationPresent(AvroTypes.avroBinary)) {
                return generatedByProcessor(resolver, possibleClassDeclaration, "AvroBinaryReader")
            }
            if (isJson && possibleClassDeclaration.isAnnotationPresent(AvroTypes.avroJson)) {
                return generatedByProcessor(resolver, possibleClassDeclaration, "AvroJsonReader")
            }

            try {
                return { generateReader(resolver, possibleClassDeclaration, isBinary, tags) }
            } catch (e: ProcessingErrorException) {
                return null
            }
        }
        return null
    }

    private fun isBinary(tags: Set<String>): Boolean = tags == setOf(AvroTypes.avroBinary.canonicalName)

    private fun isJson(tags: Set<String>): Boolean = tags == setOf(AvroTypes.avroJson.canonicalName)

    private fun generateReader(resolver: Resolver, declaration: KSClassDeclaration, isBinary: Boolean, tags: Set<String>): ExtensionResult {
        val packageElement = declaration.packageName.asString()
        val resultClassName = if (isBinary) declaration.readerBinaryName() else declaration.readerJsonName()
        val resultDeclaration = resolver.getClassDeclarationByName("$packageElement.$resultClassName")
        if (resultDeclaration != null) {
            return ExtensionResult.fromConstructor(findDefaultConstructor(resultDeclaration), resultDeclaration, tags)
        }

        if (declaration.isAnnotationPresent(AvroTypes.avroBinary)) {
            // annotation processor will handle that
            return ExtensionResult.RequiresCompilingResult
        }

        if (isBinary) {
            readerGenerator.generateBinary(declaration)
        } else {
            readerGenerator.generateJson(declaration)
        }
        return ExtensionResult.RequiresCompilingResult
    }

    private fun generateWriter(resolver: Resolver, declaration: KSClassDeclaration, isBinary: Boolean, tags: Set<String>): ExtensionResult {
        val packageElement = declaration.packageName.asString()
        val resultClassName = if (isBinary) declaration.writerBinaryName() else declaration.writerJsonName()
        val resultDeclaration = resolver.getClassDeclarationByName("$packageElement.$resultClassName")
        if (resultDeclaration != null) {
            return ExtensionResult.fromConstructor(findDefaultConstructor(resultDeclaration), resultDeclaration, tags)
        }

        if (declaration.isAnnotationPresent(AvroTypes.avroJson)) {
            // annotation processor will handle that
            return ExtensionResult.RequiresCompilingResult
        }

        if (isBinary) {
            writerGenerator.generateBinary(declaration)
        } else {
            writerGenerator.generateJson(declaration)
        }
        return ExtensionResult.RequiresCompilingResult
    }

    private fun findDefaultConstructor(resultElement: KSClassDeclaration): KSFunctionDeclaration {
        return if (resultElement.primaryConstructor != null) {
            resultElement.primaryConstructor!!
        } else if (resultElement.getConstructors().count() == 1) {
            resultElement.getConstructors().first()
        } else {
            throw ProcessingErrorException("No primary constructor found for: $resultElement", resultElement)
        }
    }
}
