package ru.tinkoff.kora.config.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.config.ksp.ConfigParserGenerator
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import java.io.IOException

class ConfigKoraExtension(resolver: Resolver, private val codeGenerator: CodeGenerator) : KoraExtension {
    private val configValueExtractorTypeErasure = resolver.getClassDeclarationByName(ConfigValueExtractor::class.java.canonicalName)!!.asStarProjectedType()
    private val configParserGenerator = ConfigParserGenerator(resolver)
    private val recordType = resolver.getClassDeclarationByName(Record::class.qualifiedName!!)!!.asStarProjectedType()

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        val actualType = if (type.nullability == Nullability.PLATFORM) type.makeNotNullable() else type
        if (actualType.starProjection() != configValueExtractorTypeErasure) {
            return null
        }
        val typeArguments = actualType.arguments
        if (typeArguments.isEmpty()) {
            return null
        }

        val paramTypeArgument = typeArguments.first()

        val declaration = paramTypeArgument.type!!.resolve().declaration

        if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.CLASS) {
            if (declaration.origin == Origin.JAVA_LIB) {
                val superTypes = declaration.superTypes.toList()
                if (superTypes.any { it.resolve() == recordType }) {
                    return { generateDependency(resolver, declaration) }
                }
            }
            val constructors = declaration.getConstructors().toList()
            if (constructors.size == 1 && constructors.first().isPublic()) {
                return { generateDependency(resolver, declaration) }
            }
        }
        return null
    }

    private fun generateDependency(resolver: Resolver, declaration: KSClassDeclaration): ExtensionResult {
        val packageName = declaration.packageName.asString()
        val typeName = declaration.getOuterClassesAsPrefix() + declaration.simpleName.getShortName() + "_" + ConfigValueExtractor::class.java.simpleName
        val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$typeName")
        if (maybeGenerated != null) {
            val constructor = getConstructor(maybeGenerated)
            return ExtensionResult.fromConstructor(constructor, maybeGenerated)
        }
        val fileSpec = configParserGenerator.generate(declaration)
        try {
            fileSpec.writeTo(codeGenerator, false)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return ExtensionResult.RequiresCompilingResult
    }

    private fun getConstructor(declaration: KSClassDeclaration): KSFunctionDeclaration {
        return declaration.getConstructors().first()
    }

}
