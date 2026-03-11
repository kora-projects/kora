package io.koraframework.http.client.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.httpClientAnnotation
import io.koraframework.kora.app.ksp.extension.ExtensionFactory
import io.koraframework.kora.app.ksp.extension.KoraExtension

class HttpClientLinkerExtensionFactory : ExtensionFactory {

    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        if (resolver.getClassDeclarationByName(httpClientAnnotation.canonicalName) != null) {
            return HttpClientKoraExtension()
        }
        return null
    }
}
