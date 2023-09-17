package ru.tinkoff.kora.http.client.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension

class HttpClientResponseMapperKoraExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        if (resolver.getClassDeclarationByName(HttpClientClassNames.httpClientResponseMapper.canonicalName) != null) {
            return HttpClientResponseMapperKoraExtension()
        }
        return null
    }
}
