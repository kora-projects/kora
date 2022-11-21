package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import org.slf4j.LoggerFactory
import ru.tinkoff.kora.json.ksp.reader.JsonClassReaderMeta
import ru.tinkoff.kora.json.ksp.reader.JsonReaderGenerator
import ru.tinkoff.kora.json.ksp.reader.ReaderTypeMetaParser
import ru.tinkoff.kora.json.ksp.reader.SealedInterfaceReaderGenerator
import ru.tinkoff.kora.json.ksp.writer.JsonClassWriterMeta
import ru.tinkoff.kora.json.ksp.writer.JsonWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.SealedInterfaceWriterGenerator
import ru.tinkoff.kora.json.ksp.writer.WriterTypeMetaParser
import java.io.IOException

@KspExperimental
class JsonProcessor(
    private val resolver: Resolver,
    private val readerTypeMetaParser: ReaderTypeMetaParser,
    private val writerTypeMetaParser: WriterTypeMetaParser,
    private val writerGenerator: JsonWriterGenerator,
    private val readerGenerator: JsonReaderGenerator,
    private val sealedReaderGenerator: SealedInterfaceReaderGenerator,
    private val sealedWriterGenerator: SealedInterfaceWriterGenerator,
    private val codeGenerator: CodeGenerator
) {
    private val log = LoggerFactory.getLogger(JsonProcessor::class.java)

    private val processedReaders: MutableSet<KSClassDeclaration> = HashSet()
    private val processedWriters: MutableSet<KSClassDeclaration> = HashSet()
    private val nonProcessedReaders: MutableSet<KSClassDeclaration> = HashSet()
    private val nonProcessedWriters: MutableSet<KSClassDeclaration> = HashSet()


    fun generateReader(jsonClassDeclaration: KSClassDeclaration) {
        val packageElement = jsonClassPackage(jsonClassDeclaration)
        val type = jsonClassDeclaration.asStarProjectedType()
        val jsonTypeRef = resolver.createKSTypeReferenceFromKSType(type)
        if (processedReaders.contains(jsonClassDeclaration)) {
            return
        }
        val readerClassName = jsonReaderName(jsonClassDeclaration)
        val readerDeclaration = resolver.getClassDeclarationByName("$packageElement.$readerClassName")
        if (readerDeclaration != null) {
            processedReaders.add(jsonClassDeclaration)
            return
        }
        val requiredDiscriminator = hashSetOf<KSClassDeclaration>()
        if (isSealed(jsonClassDeclaration)) {
            val jsonElements = jsonClassDeclaration.getSealedSubclasses().toList()
            jsonElements.forEach { sealedSub ->
                nonProcessedReaders.add(sealedSub)
                requiredDiscriminator.add(sealedSub)
            }
            val sealedReaderTypes = sealedReaderGenerator.generateSealedReader(jsonTypeRef, jsonElements)
            sealedReaderTypes.forEach { sealedReaderType ->
                try {
                    val fileSpec = FileSpec.builder(
                        packageName = packageElement,
                        fileName = sealedReaderType.name!!
                    )
                    fileSpec.addType(sealedReaderType)
                    fileSpec.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        } else nonProcessedReaders.add(jsonClassDeclaration)
        val failedReaders = HashSet<KSClassDeclaration>()
        while (failedReaders.size < nonProcessedReaders.size) {
            val processedReadersAtStart = processedReaders.size
            for (jsonClass in nonProcessedReaders) {
                val typeRef = resolver.createKSTypeReferenceFromKSType(jsonClass.asStarProjectedType())
                log.info("Generating JsonReader for {}", jsonClass)
                if (failedReaders.contains(jsonClass)) {
                    continue
                }
                try {
                    val discriminatorRequired = requiredDiscriminator.contains(jsonClass)
                    tryGenerateReader(typeRef, discriminatorRequired)
                } catch (e: Exception) {
                    failedReaders.add(jsonClass)
                }
            }
            nonProcessedReaders.removeAll(processedReaders)
            val processedReadersAtEnd = processedReaders.size
            if (processedReadersAtEnd != processedReadersAtStart) {
                failedReaders.clear()
            }
        }
    }

    fun tryGenerateReader(jsonTypeRef: KSTypeReference, discriminatorRequired: Boolean = false) {
        val classDeclaration = jsonTypeRef.resolve().declaration as KSClassDeclaration
        val meta = readerTypeMetaParser.parse(jsonTypeRef, discriminatorRequired) ?: throw RuntimeException("Can't parse meta data")
        generateReaderByMeta(meta.copy(type = meta.type.makeNotNullable()))
        processedReaders.add(classDeclaration)
    }

    private fun generateReaderByMeta(meta: JsonClassReaderMeta) {
        val packageElement = jsonClassPackage(meta.type.declaration as KSClassDeclaration)
        val readerClassName = jsonReaderName(meta.type)
        val readerElement = resolver.getClassDeclarationByName("$packageElement.$readerClassName")
        if (readerElement == null) {
            val readerType = readerGenerator.generate(meta)
            try {
                val fileSpec = FileSpec.builder(packageElement, jsonReaderName(meta.type)).indent("    ")
                fileSpec.addType(readerType)
                fileSpec.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    fun generateWriter(jsonClassDeclaration: KSClassDeclaration) {
        val jsonTypeRef = resolver.createKSTypeReferenceFromKSType(jsonClassDeclaration.asStarProjectedType())
        val packageElement = jsonClassPackage(jsonClassDeclaration)
        if (processedWriters.contains(jsonClassDeclaration)) {
            return
        }
        val writerClassName = jsonWriterName(jsonClassDeclaration)
        val writerElement = resolver.getClassDeclarationByName("$packageElement.$writerClassName")
        if (writerElement != null) {
            processedWriters.add(jsonClassDeclaration)
            return
        }
        val requiresDiscriminator = HashSet<KSClassDeclaration>()
        if (isSealed(jsonClassDeclaration)) {
            val jsonElements = jsonClassDeclaration.getSealedSubclasses().toList()
            jsonElements.forEach { sealedSub ->
                nonProcessedWriters.add(sealedSub)
                requiresDiscriminator.add(sealedSub)
            }
            val sealedWriterTypes = sealedWriterGenerator.generateSealedWriter(jsonTypeRef, jsonElements)
            sealedWriterTypes.forEach { sealedReaderType ->
                try {
                    val fileSpec = FileSpec.builder(
                        packageName = packageElement,
                        fileName = sealedReaderType.name!!
                    )
                    fileSpec.addType(sealedReaderType)
                    fileSpec.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
                } catch (_: FileAlreadyExistsException) {
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        } else nonProcessedWriters.add(jsonClassDeclaration)
        val failedWriters = HashSet<KSClassDeclaration>()
        while (failedWriters.size < nonProcessedWriters.size) {
            val newClassesToProcess = hashSetOf<KSClassDeclaration>()
            val processedWritersAtStart = processedWriters.size
            for (jsonClass in nonProcessedWriters) {
                val typeRef = resolver.createKSTypeReferenceFromKSType(jsonClass.asStarProjectedType())
                log.info("Generating JsonWriter for {}", jsonClass)
                if (failedWriters.contains(jsonClass)) {
                    continue
                }
                val discriminatorRequired = requiresDiscriminator.contains(jsonClass)
                val moreClasses = tryGenerateWriter(typeRef, discriminatorRequired)
                if (moreClasses != null) {
                    newClassesToProcess.addAll(moreClasses)
                    if (moreClasses.isEmpty()) {
                        processedWriters.add(jsonClass)
                    } else {
                        failedWriters.add(jsonClass)
                    }
                } else {
                    failedWriters.add(jsonClass)
                }
            }
            nonProcessedWriters.addAll(newClassesToProcess)
            nonProcessedWriters.removeAll(processedWriters)
            val processedReadersAtEnd = processedWriters.size
            if (processedReadersAtEnd != processedWritersAtStart) {
                failedWriters.clear()
            }
        }
    }

    fun tryGenerateWriter(jsonTypeMirror: KSTypeReference, discriminatorRequired: Boolean = false): Set<KSClassDeclaration>? {
        val meta = writerTypeMetaParser.parse(jsonTypeMirror, discriminatorRequired) ?: return null
        generateWriterByMeta(meta.copy(type = meta.type.makeNotNullable()))
        processedWriters.add(meta.type.declaration as KSClassDeclaration)
        return setOf()
    }

    private fun generateWriterByMeta(meta: JsonClassWriterMeta) {
        val packageElement = meta.type.declaration.packageName.asString()
        val writerClassName = jsonWriterName(meta.type)
        val writerElement = resolver.getClassDeclarationByName("$packageElement.$writerClassName")
        if (writerElement == null) {
            val writerType = writerGenerator.generate(meta)
            try {
                val fileSpec = FileSpec.builder(
                    packageName = packageElement,
                    fileName = writerClassName
                ).indent("    ")
                fileSpec.addType(writerType)
                fileSpec.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
            } catch (_: FileAlreadyExistsException) {
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

}
