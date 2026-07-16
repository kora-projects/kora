package io.koraframework.avro.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import io.koraframework.avro.symbol.processor.AvroTypes
import io.koraframework.avro.symbol.processor.reader.AvroReaderGenerator
import io.koraframework.avro.symbol.processor.readerName
import io.koraframework.avro.symbol.processor.writer.AvroWriterGenerator
import io.koraframework.avro.symbol.processor.writerName
import io.koraframework.kora.app.ksp.extension.ExtensionResult
import io.koraframework.kora.app.ksp.extension.ExtensionResult.CodeBlockResult
import io.koraframework.kora.app.ksp.extension.KoraExtension
import io.koraframework.ksp.common.exception.ProcessingErrorException

class AvroKoraExtension(
    private val resolver: Resolver,
    kspLogger: KSPLogger,
    codeGenerator: CodeGenerator
) : KoraExtension {
    private val writerErasure = resolver.getClassDeclarationByName(AvroTypes.writer.canonicalName)!!.asStarProjectedType()
    private val readerErasure = resolver.getClassDeclarationByName(AvroTypes.reader.canonicalName)!!.asStarProjectedType()
    private val specificRecord = resolver.getClassDeclarationByName(AvroTypes.specificRecord.canonicalName)!!.asStarProjectedType()
    private val readerGenerator = AvroReaderGenerator(resolver, codeGenerator)
    private val writerGenerator = AvroWriterGenerator(resolver, codeGenerator)
    private val generatedMappers = HashSet<String>()

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        if (tag != null && tag != AvroTypes.avro.canonicalName) {
            return null
        }

        val requestedType = type.makeNotNullable()
        if (writerErasure.isAssignableFrom(requestedType)) {
            val declaration = dependencyTarget(requestedType, "AvroWriter") ?: return null
            return generateMapper(declaration, declaration.writerName(), requestedType, tag) {
                writerGenerator.generate(declaration)
            }
        }

        if (readerErasure.isAssignableFrom(requestedType)) {
            val declaration = dependencyTarget(requestedType, "AvroReader") ?: return null
            return generateMapper(declaration, declaration.readerName(), requestedType, tag) {
                readerGenerator.generate(declaration)
            }
        }

        return null
    }

    private fun dependencyTarget(type: KSType, dependencyName: String): KSClassDeclaration? {
        val targetType = type.arguments.firstOrNull()?.type?.resolve() ?: return null
        val declaration = targetType.declaration
        if (declaration !is KSClassDeclaration
            || declaration.modifiers.contains(Modifier.ENUM)
            || declaration.modifiers.contains(Modifier.SEALED)
            || !specificRecord.isAssignableFrom(targetType)
        ) {
            throw ProcessingErrorException("$dependencyName<T> can only be created for concrete org.apache.avro.specific.SpecificRecord types", declaration)
        }
        return declaration
    }

    private fun generateMapper(
        declaration: KSClassDeclaration,
        mapperName: String,
        requestedType: KSType,
        tag: String?,
        generate: () -> Unit
    ): () -> ExtensionResult {
        return {
            val packageName = declaration.packageName.asString()
            val mapperCanonicalName = "$packageName.$mapperName"
            if (resolver.getClassDeclarationByName(mapperCanonicalName) == null && generatedMappers.add(mapperCanonicalName)) {
                generate()
            }

            CodeBlockResult(
                declaration,
                { CodeBlock.of("%T()", ClassName(packageName, mapperName)) },
                requestedType,
                tag,
                emptyList(),
                emptyList()
            )
        }
    }
}
