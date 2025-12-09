package ru.tinkoff.kora.validation.symbol.processor.extension

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.VALIDATOR_TYPE
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.VALID_TYPE

class ValidKoraExtension() : KoraExtension {

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        if (tag != null) return null
        type.toTypeName().let {
            if (it is ParameterizedTypeName && it.rawType == VALIDATOR_TYPE) {
                val validTypeDecl = type.arguments.first().type?.resolve()?.declaration
                if (validTypeDecl?.isAnnotationPresent(VALID_TYPE) == true) {
                    return generatedByProcessor(resolver, validTypeDecl as KSClassDeclaration, VALIDATOR_TYPE)
                }
            }
        }
        return null
    }
}
