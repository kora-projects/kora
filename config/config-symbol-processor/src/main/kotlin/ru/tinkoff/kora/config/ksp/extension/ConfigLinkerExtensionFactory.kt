package ru.tinkoff.kora.config.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension

class ConfigLinkerExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        val configValueExtractor = resolver.getClassDeclarationByName(ConfigValueExtractor::class.java.canonicalName)
        return configValueExtractor?.let { ConfigKoraExtension(resolver, codeGenerator) }
    }
}
