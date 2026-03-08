package io.koraframework.validation.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.kora.app.ksp.extension.ExtensionFactory
import io.koraframework.kora.app.ksp.extension.KoraExtension
import io.koraframework.validation.symbol.processor.ValidTypes.VALID_TYPE

class ValidKoraExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        val json = resolver.getClassDeclarationByName(VALID_TYPE.canonicalName)
        return if (json == null) {
            null
        } else {
            ValidKoraExtension()
        }
    }
}
