package ru.tinkoff.kora.validation.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.generatedClass
import ru.tinkoff.kora.validation.symbol.processor.VALIDATOR_TYPE
import ru.tinkoff.kora.validation.symbol.processor.VALID_TYPE
import ru.tinkoff.kora.validation.symbol.processor.ValidatorGenerator

class ValidKoraExtension(resolver: Resolver, codeGenerator: CodeGenerator) : KoraExtension {
    private val gen = ValidatorGenerator(codeGenerator)

    private val validatorType = resolver.getClassDeclarationByName(VALIDATOR_TYPE.canonicalName)!!.asStarProjectedType()

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) return null
        val actualType = if (type.nullability == Nullability.PLATFORM) type.makeNotNullable() else type
        val erasure = actualType.starProjection()
        if (erasure == validatorType) {
            val possibleJsonClass = type.arguments[0]
            return { generateValidator(resolver, possibleJsonClass) }
        }

        return null
    }

    private fun generateValidator(resolver: Resolver, componentArgumentType: KSTypeArgument): ExtensionResult {
        val argumentType = componentArgumentType.type!!.resolve()
        val argumentTypeClass = argumentType.declaration as KSClassDeclaration
        val packageElement = argumentTypeClass.packageName.asString()
        val validatorName = argumentTypeClass.generatedClass("Validator")

        val resultDeclaration = resolver.getClassDeclarationByName("$packageElement.$validatorName")
        if (resultDeclaration != null) {
            return ExtensionResult.fromConstructor(findDefaultConstructor(resultDeclaration), resultDeclaration)
        }
        if (argumentTypeClass.findAnnotation(VALID_TYPE) != null) {
            return ExtensionResult.RequiresCompilingResult
        }
        gen.generate(argumentTypeClass)
        return ExtensionResult.RequiresCompilingResult
    }

    private fun findDefaultConstructor(resultElement: KSClassDeclaration): KSFunctionDeclaration {
        return resultElement.getConstructors().firstOrNull() ?: throw NoSuchElementException("Default constructor not found for: $resultElement")
    }
}
