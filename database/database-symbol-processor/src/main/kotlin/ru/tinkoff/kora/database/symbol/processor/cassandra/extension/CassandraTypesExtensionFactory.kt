package ru.tinkoff.kora.database.symbol.processor.cassandra.extension

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory

class CassandraTypesExtensionFactory: ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator) = CassandraTypesExtension(resolver, kspLogger, codeGenerator)
}
