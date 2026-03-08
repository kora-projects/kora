package io.koraframework.config.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.config.ksp.ConfigClassNames
import io.koraframework.kora.app.ksp.extension.ExtensionFactory
import io.koraframework.kora.app.ksp.extension.KoraExtension

class ConfigLinkerExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        val configValueExtractor = resolver.getClassDeclarationByName(ConfigClassNames.configValueExtractor.canonicalName)
        return configValueExtractor?.let { ConfigKoraExtension() }
    }
}
