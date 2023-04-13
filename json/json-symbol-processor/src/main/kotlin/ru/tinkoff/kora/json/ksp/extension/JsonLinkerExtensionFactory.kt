package ru.tinkoff.kora.json.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension

class JsonLinkerExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        val json = resolver.getClassDeclarationByName(JsonTypes.json.canonicalName)
        return if (json == null) {
            null
        } else {
            JsonKoraExtension(resolver, kspLogger, codeGenerator)
        }
    }
}
