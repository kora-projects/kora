package ru.tinkoff.kora.database.symbol.processor.r2dbc.extension

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory

class R2dbcTypesExtensionFactory: ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator) = R2dbcTypesExtension(resolver, kspLogger, codeGenerator)
}
