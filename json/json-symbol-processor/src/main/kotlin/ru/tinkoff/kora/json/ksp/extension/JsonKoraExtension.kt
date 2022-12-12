package ru.tinkoff.kora.json.ksp.extension

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.ksp.JsonProcessor
import ru.tinkoff.kora.json.ksp.KnownType
import ru.tinkoff.kora.json.ksp.jsonReaderName
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.json.ksp.reader.JsonReaderGenerator
import ru.tinkoff.kora.json.ksp.reader.ReaderTypeMetaParser
import ru.tinkoff.kora.json.ksp.reader.SealedInterfaceReaderGenerator
import ru.tinkoff.kora.json.ksp.writer.JsonWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.SealedInterfaceWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.WriterTypeMetaParser
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

@OptIn(KspExperimental::class)
class JsonKoraExtension(
    private val resolver: Resolver,
    private val kspLogger: KSPLogger,
    codeGenerator: CodeGenerator
) : KoraExtension {
    private val jsonWriterErasure = resolver.getClassDeclarationByName(JsonWriter::class.qualifiedName!!)!!.asStarProjectedType()
    private val jsonReaderErasure = resolver.getClassDeclarationByName(JsonReader::class.qualifiedName!!)!!.asStarProjectedType()
    private val knownTypes = KnownType(resolver)
    private val readerTypeMetaParser: ReaderTypeMetaParser = ReaderTypeMetaParser(resolver, knownTypes, kspLogger)
    private val writerTypeMetaParser: WriterTypeMetaParser = WriterTypeMetaParser(resolver, kspLogger)
    private val writerGenerator = JsonWriterGenerator(resolver)
    private val readerGenerator = JsonReaderGenerator(resolver)
    private val sealedReaderGenerator = SealedInterfaceReaderGenerator(resolver, kspLogger)
    private val sealedWriterGenerator = SealedInterfaceWriterGenerator(resolver, kspLogger)
    private val processor: JsonProcessor = JsonProcessor(
        resolver,
        readerTypeMetaParser,
        writerTypeMetaParser,
        writerGenerator,
        readerGenerator,
        sealedReaderGenerator,
        sealedWriterGenerator,
        codeGenerator
    )

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        val actualType = if (type.nullability == Nullability.PLATFORM) type.makeNotNullable() else type
        val erasure = actualType.starProjection()
        if (erasure == jsonWriterErasure) {
            val possibleJsonClass = type.arguments[0]
            val writerMeta = writerTypeMetaParser.parse(possibleJsonClass.type!!)
            if (writerMeta != null && (writerMeta.isSealedStructure || isProcessableType(writerMeta.type))) {
                return { generateWriter(resolver, possibleJsonClass) }
            }
        }
        if (erasure == jsonReaderErasure) {
            val possibleJsonClass = type.arguments[0]
            val readerMeta = readerTypeMetaParser.parse(possibleJsonClass.type!!)
            if (readerMeta == null) {
                return null
            }
            if (readerMeta.isSealedStructure) {
                if (readerMeta.discriminatorField != null) {
                    return { generateReader(resolver, possibleJsonClass) }
                } else {
                    throw ProcessingErrorException(
                        "Unspecified discriminator field for sealed interface, please use @JsonDiscriminatorField annotation",
                        readerMeta.type.declaration
                    )
                }
            }
            if (isProcessableType(readerMeta.type)) {
                return { generateReader(resolver, possibleJsonClass) }
            }
            return null
        }
        return null
    }

    private fun generateReader(resolver: Resolver, jsonTypeArgument: KSTypeArgument): ExtensionResult {
        val jsonType = jsonTypeArgument.type!!.resolve()
        val jsonClass = jsonType.declaration as KSClassDeclaration
        val packageElement = jsonClass.packageName.asString()
        val resultClassName = jsonReaderName(jsonType)
        val resultDeclaration = resolver.getClassDeclarationByName("$packageElement.$resultClassName")
        if (resultDeclaration != null) {
            return ExtensionResult.fromConstructor(findDefaultConstructor(resultDeclaration), resultDeclaration)
        }
        val hasJsonConstructor: Boolean = jsonClass.getConstructors().filter { !it.isPrivate() }.any { it.isAnnotationPresent(ru.tinkoff.kora.json.common.annotation.JsonReader::class) }
        if (hasJsonConstructor || jsonClass.isAnnotationPresent(ru.tinkoff.kora.json.common.annotation.JsonReader::class)) {
            // annotation processor will handle that
            return ExtensionResult.RequiresCompilingResult
        }
        processor.tryGenerateReader(jsonTypeArgument.type!!)
        return ExtensionResult.RequiresCompilingResult
    }

    private fun generateWriter(resolver: Resolver, jsonTypeArgument: KSTypeArgument): ExtensionResult {
        val jsonType = jsonTypeArgument.type!!.resolve()
        val jsonClass = jsonType.declaration as KSClassDeclaration
        val packageElement = jsonClass.packageName.asString()
        val resultClassName = jsonWriterName(jsonType)
        val resultDeclaration = resolver.getClassDeclarationByName("$packageElement.$resultClassName")
        if (resultDeclaration != null) {
            return ExtensionResult.fromConstructor(findDefaultConstructor(resultDeclaration), resultDeclaration)
        }
        if (jsonClass.isAnnotationPresent(Json::class) || jsonClass.isAnnotationPresent(ru.tinkoff.kora.json.common.annotation.JsonWriter::class)) {
            // annotation processor will handle that
            return ExtensionResult.RequiresCompilingResult
        }
        processor.tryGenerateWriter(jsonTypeArgument.type!!)
        return ExtensionResult.RequiresCompilingResult
    }

    private fun findDefaultConstructor(resultElement: KSClassDeclaration): KSFunctionDeclaration {
        return resultElement.primaryConstructor ?: throw NoSuchElementException("No value present")
    }

    private fun isProcessableType(typeRef: KSType): Boolean {
        val declaration = typeRef.declaration as KSClassDeclaration
        val classKind = declaration.classKind
        return classKind == ClassKind.ENUM_CLASS || (classKind == ClassKind.CLASS && !typeRef.declaration.isOpen())
    }
}
