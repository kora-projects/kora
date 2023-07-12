package ru.tinkoff.kora.json.ksp.extension

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import ru.tinkoff.kora.json.ksp.*
import ru.tinkoff.kora.json.ksp.reader.ReaderTypeMetaParser
import ru.tinkoff.kora.json.ksp.writer.WriterTypeMetaParser
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

class JsonKoraExtension(
    private val resolver: Resolver,
    private val kspLogger: KSPLogger,
    codeGenerator: CodeGenerator
) : KoraExtension {
    private val jsonWriterErasure = resolver.getClassDeclarationByName(JsonTypes.jsonWriter.canonicalName)!!.asStarProjectedType()
    private val jsonReaderErasure = resolver.getClassDeclarationByName(JsonTypes.jsonReader.canonicalName)!!.asStarProjectedType()
    private val knownTypes = KnownType(resolver)
    private val readerTypeMetaParser: ReaderTypeMetaParser = ReaderTypeMetaParser(knownTypes, kspLogger)
    private val writerTypeMetaParser: WriterTypeMetaParser = WriterTypeMetaParser(resolver)
    private val processor: JsonProcessor = JsonProcessor(resolver, kspLogger, codeGenerator, knownTypes)

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        val actualType = type.makeNotNullable()
        val erasure = actualType.starProjection()
        if (erasure == jsonWriterErasure) {
            val possibleJsonClass = type.arguments[0].type!!.resolve()
            if (possibleJsonClass.isMarkedNullable) {
                val jsonWriterDecl = resolver.getClassDeclarationByName(JsonTypes.jsonWriter.canonicalName)!!
                val functionDecl = resolver.getFunctionDeclarationsByName("ru.tinkoff.kora.json.common.JsonKotlin.writerForNullable").first()
                val writerType = jsonWriterDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass), Variance.INVARIANT)
                    )
                )
                val delegateType = jsonWriterDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass.makeNotNullable()), Variance.INVARIANT)
                    )
                )
                val functionType = functionDecl.parametrized(writerType, listOf(delegateType))
                return { ExtensionResult.fromExecutable(functionDecl, functionType) }
            }
            val possibleJsonClassDeclaration = possibleJsonClass.declaration
            if (possibleJsonClassDeclaration !is KSClassDeclaration) {
                return null
            }
            if (possibleJsonClassDeclaration.isAnnotationPresent(JsonTypes.json) || possibleJsonClassDeclaration.isAnnotationPresent(JsonTypes.jsonWriter)) {
                return generatedByProcessor(resolver, possibleJsonClassDeclaration, "JsonWriter")
            }
            if (possibleJsonClassDeclaration.modifiers.contains(Modifier.ENUM) || possibleJsonClassDeclaration.modifiers.contains(Modifier.SEALED)) {
                return { generateWriter(resolver, possibleJsonClassDeclaration) }
            }
            try {
                writerTypeMetaParser.parse(possibleJsonClassDeclaration)
                return { generateWriter(resolver, possibleJsonClassDeclaration) }
            } catch (e: ProcessingErrorException) {
                e.message?.let { kspLogger.warn(it, null) }
                return null
            }
        }
        if (erasure == jsonReaderErasure) {
            val possibleJsonClass = type.arguments[0].type!!.resolve()
            if (possibleJsonClass.isMarkedNullable) {
                val jsonReaderDecl = resolver.getClassDeclarationByName(JsonTypes.jsonReader.canonicalName)!!
                val functionDecl = resolver.getFunctionDeclarationsByName("ru.tinkoff.kora.json.common.JsonKotlin.readerForNullable").first()
                val readerType = jsonReaderDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass), Variance.INVARIANT)
                    )
                )
                val delegateType = jsonReaderDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass.makeNotNullable()), Variance.INVARIANT)
                    )
                )
                val functionType = functionDecl.parametrized(readerType, listOf(delegateType))
                return { ExtensionResult.fromExecutable(functionDecl, functionType) }
            }
            val possibleJsonClassDeclaration = possibleJsonClass.declaration
            if (possibleJsonClassDeclaration !is KSClassDeclaration) {
                return null
            }
            if (possibleJsonClassDeclaration.isAnnotationPresent(JsonTypes.json)
                || possibleJsonClassDeclaration.isAnnotationPresent(JsonTypes.jsonReader)
                || possibleJsonClassDeclaration.primaryConstructor?.isAnnotationPresent(JsonTypes.jsonReader) == true
            ) {
                return generatedByProcessor(resolver, possibleJsonClassDeclaration, "JsonReader")
            }
            if (possibleJsonClassDeclaration.modifiers.contains(Modifier.ENUM) || possibleJsonClassDeclaration.modifiers.contains(Modifier.SEALED)) {
                return { generateReader(resolver, possibleJsonClassDeclaration) }
            }

            try {
                readerTypeMetaParser.parse(possibleJsonClassDeclaration)
                return { generateReader(resolver, possibleJsonClassDeclaration) }
            } catch (e: ProcessingErrorException) {
                return null
            }
        }
        return null
    }

    private fun generateReader(resolver: Resolver, jsonClass: KSClassDeclaration): ExtensionResult {
        val packageElement = jsonClass.packageName.asString()
        val resultClassName = jsonClass.jsonReaderName()
        val resultDeclaration = resolver.getClassDeclarationByName("$packageElement.$resultClassName")
        if (resultDeclaration != null) {
            return ExtensionResult.fromConstructor(findDefaultConstructor(resultDeclaration), resultDeclaration)
        }
        val hasJsonConstructor = jsonClass.getConstructors().filter { !it.isPrivate() }.any { it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation) }
        if (hasJsonConstructor || jsonClass.isAnnotationPresent(JsonTypes.jsonReaderAnnotation)) {
            // annotation processor will handle that
            return ExtensionResult.RequiresCompilingResult
        }
        processor.generateReader(jsonClass)
        return ExtensionResult.RequiresCompilingResult
    }

    private fun generateWriter(resolver: Resolver, declaration: KSClassDeclaration): ExtensionResult {
        val packageElement = declaration.packageName.asString()
        val resultClassName = declaration.jsonWriterName()
        val resultDeclaration = resolver.getClassDeclarationByName("$packageElement.$resultClassName")
        if (resultDeclaration != null) {
            return ExtensionResult.fromConstructor(findDefaultConstructor(resultDeclaration), resultDeclaration)
        }
        if (declaration.isAnnotationPresent(JsonTypes.json) || declaration.isAnnotationPresent(JsonTypes.jsonWriterAnnotation)) {
            // annotation processor will handle that
            return ExtensionResult.RequiresCompilingResult
        }
        processor.generateWriter(declaration)
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
