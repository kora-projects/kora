package io.koraframework.mapstruct.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.kora.app.ksp.extension.ExtensionFactory
import io.koraframework.kora.app.ksp.extension.KoraExtension

class MapstructKoraExtensionFactory : ExtensionFactory {

    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        return resolver.getClassDeclarationByName(MapstructKoraExtension.mapperAnnotation.canonicalName)
            ?.let { MapstructKoraExtension }
    }
}
