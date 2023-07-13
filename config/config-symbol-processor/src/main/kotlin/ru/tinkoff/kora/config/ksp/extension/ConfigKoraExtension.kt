package ru.tinkoff.kora.config.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.config.ksp.ConfigClassNames
import ru.tinkoff.kora.config.ksp.ConfigParserGenerator
import ru.tinkoff.kora.config.ksp.ConfigUtils
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.JavaUtils.isRecord
import ru.tinkoff.kora.ksp.common.generatedClassName
import java.io.IOException

class ConfigKoraExtension(resolver: Resolver, private val codeGenerator: CodeGenerator) : KoraExtension {
    private val configValueExtractorTypeErasure = resolver.getClassDeclarationByName(ConfigClassNames.configValueExtractor.canonicalName)!!.asStarProjectedType()
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

        val configType = paramTypeArgument.type!!.resolve()
        val declaration = configType.declaration
        if (declaration !is KSClassDeclaration) {
            return null
        }
        if (declaration.isAnnotationPresent(ConfigClassNames.configSourceAnnotation) || declaration.isAnnotationPresent(ConfigClassNames.configValueExtractorAnnotation)) {
            return generatedByProcessor(resolver, declaration, ConfigClassNames.configValueExtractor.simpleName)
        }

        if (declaration.classKind == ClassKind.CLASS && declaration.modifiers.contains(Modifier.DATA)) {
            return lambda@{
                var maybeGenerated = maybeGenerated(resolver, declaration)
                if (maybeGenerated != null) {
                    return@lambda maybeGenerated
                } else {
                    val result = configParserGenerator.generateForDataClass(codeGenerator, configType, true)
                    require(result.isLeft)
                    return@lambda ExtensionResult.RequiresCompilingResult
                }
            }
        }
        if (declaration.classKind == ClassKind.INTERFACE) {
            val fields = ConfigUtils.parseFields(resolver, declaration)
            if (fields.isRight) {
                return null
            }
            return lambda@{
                var maybeGenerated = maybeGenerated(resolver, declaration)
                if (maybeGenerated != null) {
                    return@lambda maybeGenerated
                } else {
                    val result = configParserGenerator.generateForInterface(codeGenerator, configType, true)
                    require(result.isLeft)
                    return@lambda ExtensionResult.RequiresCompilingResult
                }
            }
        }
        if (declaration.classKind == ClassKind.CLASS) {
            val fields = ConfigUtils.parseFields(resolver, declaration)
            if (fields.isRight) {
                return null
            }
            return lambda@{
                var maybeGenerated = maybeGenerated(resolver, declaration)
                if (maybeGenerated != null) {
                    return@lambda maybeGenerated
                } else {
                    if (declaration.isRecord()) {
                        val result = configParserGenerator.generateForRecord(codeGenerator, configType)
                        require(result.isLeft)
                        return@lambda ExtensionResult.RequiresCompilingResult
                    } else {
                        val result = configParserGenerator.generateForPojo(codeGenerator, configType)
                        require(result.isLeft)
                        return@lambda ExtensionResult.RequiresCompilingResult
                    }
                }
            }

        }
        return null
    }

    private fun maybeGenerated(resolver: Resolver, declaration: KSClassDeclaration): ExtensionResult? {
        val packageName = declaration.packageName.asString()
        val typeName = declaration.generatedClassName("ConfigValueExtractor")
        val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$typeName")
        if (maybeGenerated != null) {
            val constructor = getConstructor(maybeGenerated)
            return ExtensionResult.fromConstructor(constructor, maybeGenerated)
        } else {
            return null
        }
    }

    private fun getConstructor(declaration: KSClassDeclaration): KSFunctionDeclaration {
        return declaration.getConstructors().first()
    }
}
