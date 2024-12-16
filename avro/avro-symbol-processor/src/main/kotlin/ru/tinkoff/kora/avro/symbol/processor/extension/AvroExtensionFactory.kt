package ru.tinkoff.kora.avro.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.avro.symbol.processor.AvroTypes
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension

class AvroExtensionFactory : ExtensionFactory {

    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        val avro = resolver.getClassDeclarationByName(AvroTypes.avroBinary.canonicalName)
        return if (avro == null) {
            null
        } else {
            AvroKoraExtension(resolver, kspLogger, codeGenerator)
        }
    }
}
