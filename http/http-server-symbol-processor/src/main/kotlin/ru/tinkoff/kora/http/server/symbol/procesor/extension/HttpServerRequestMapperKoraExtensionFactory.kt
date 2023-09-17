package ru.tinkoff.kora.http.server.symbol.procesor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension

class HttpServerRequestMapperKoraExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        if (resolver.getClassDeclarationByName(HttpServerClassNames.httpServerRequestMapper.canonicalName) != null) {
            return HttpServerRequestMapperKoraExtension()
        }
        return null
    }
}
