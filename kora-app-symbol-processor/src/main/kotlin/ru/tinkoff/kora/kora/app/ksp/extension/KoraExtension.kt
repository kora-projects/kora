package ru.tinkoff.kora.kora.app.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.ksp.common.generatedClassName

interface KoraExtension {
    fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)?

    fun generatedByProcessor(resolver: Resolver, source: KSClassDeclaration, postfix: String): (() -> ExtensionResult)? {
        val generatedTypeName = source.generatedClassName(postfix)
        val packageName = source.packageName.asString()
        return {
            val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$generatedTypeName")
            if (maybeGenerated == null) {
                ExtensionResult.RequiresCompilingResult
            } else {
                ExtensionResult.fromConstructor(maybeGenerated.primaryConstructor!!, maybeGenerated)
            }
        }
    }
}
