package io.koraframework.avro.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.avro.symbol.processor.AvroTypes
import io.koraframework.kora.app.ksp.extension.ExtensionFactory
import io.koraframework.kora.app.ksp.extension.KoraExtension

class AvroExtensionFactory : ExtensionFactory {

    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        val avro = resolver.getClassDeclarationByName(AvroTypes.avro.canonicalName)
        return if (avro == null) {
            null
        } else {
            AvroKoraExtension(resolver, kspLogger, codeGenerator)
        }
    }
}
