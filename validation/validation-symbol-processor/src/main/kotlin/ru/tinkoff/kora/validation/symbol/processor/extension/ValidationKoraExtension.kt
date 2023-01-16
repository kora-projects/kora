package ru.tinkoff.kora.validation.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension

class ValidationKoraExtension(resolver: Resolver) : KoraExtension {

    private val validatorType = resolver.getClassDeclarationByName("ru.tinkoff.kora.validation.common.Validator")!!.asStarProjectedType()

    override fun getDependencyGenerator(resolver: Resolver, type: KSType): (() -> ExtensionResult)? {
        val actualType = if (type.nullability == Nullability.PLATFORM) type.makeNotNullable() else type
        val erasure = actualType.starProjection()
        if (erasure == validatorType) {
            val possibleJsonClass = type.arguments[0]
            return { generateWriter(resolver, possibleJsonClass) }
        }

        return null
    }

    private fun generateWriter(resolver: Resolver, componentArgumentType: KSTypeArgument): ExtensionResult {
        val argumentType = componentArgumentType.type!!.resolve()
        val argumentTypeClass = argumentType.declaration as KSClassDeclaration
        val packageElement = argumentTypeClass.packageName.asString()
        val validatorName = "$" + argumentTypeClass.simpleName.asString() + "_Validator"

        val resultDeclaration = resolver.getClassDeclarationByName("$packageElement.$validatorName")
        if (resultDeclaration != null) {
            return ExtensionResult.fromConstructor(findDefaultConstructor(resultDeclaration), resultDeclaration)
        }

        return ExtensionResult.RequiresCompilingResult
    }

    private fun findDefaultConstructor(resultElement: KSClassDeclaration): KSFunctionDeclaration {
        return resultElement.getConstructors().firstOrNull() ?: throw NoSuchElementException("Default constructor not found for: $resultElement")
    }
}
