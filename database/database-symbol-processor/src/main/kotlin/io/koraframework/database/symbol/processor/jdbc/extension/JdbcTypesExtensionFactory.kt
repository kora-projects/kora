package io.koraframework.database.symbol.processor.jdbc.extension

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.kora.app.ksp.extension.ExtensionFactory

class JdbcTypesExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator) = JdbcTypesExtension()
}
