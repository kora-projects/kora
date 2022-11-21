package ru.tinkoff.kora.database.symbol.processor.extension

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension

@KspExperimental
class RepositoryLinkerExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        val repository = resolver.getClassDeclarationByName(resolver.getKSNameFromString(DbUtils.repositoryAnnotation.canonicalName))
        return repository?.let { RepositoryKoraExtension(kspLogger) }
    }
}
