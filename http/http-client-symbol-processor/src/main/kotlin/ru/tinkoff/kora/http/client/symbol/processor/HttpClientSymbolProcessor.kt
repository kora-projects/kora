package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientAnnotation
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.visitClass

class HttpClientSymbolProcessor(val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    private lateinit var clientGenerator: ClientClassGenerator
    private lateinit var configGenerator: ConfigClassGenerator
    private lateinit var configModuleGenerator: ConfigModuleGenerator

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        clientGenerator = ClientClassGenerator(resolver)
        configGenerator = ConfigClassGenerator()
        configModuleGenerator = ConfigModuleGenerator(resolver)

        val symbols = resolver.getSymbolsWithAnnotation(httpClientAnnotation.canonicalName).toList()
        symbols.forEach {
            it.visitClass { declaration ->
                if (declaration.classKind == ClassKind.INTERFACE) {
                    generateClient(declaration, resolver)
                }
            }
        }
        return emptyList()
    }

    private fun generateClient(declaration: KSClassDeclaration, resolver: Resolver) {
        val packageName = declaration.packageName.asString()
        val client = clientGenerator.generate(declaration)
        val config = configGenerator.generate(declaration)
        val configModule = configModuleGenerator.generate(declaration)

        configModule.writeTo(environment.codeGenerator, false)
        FileSpec.get(packageName, client).writeTo(environment.codeGenerator, false)
        FileSpec.get(packageName, config).writeTo(environment.codeGenerator, false)
    }
}

class HttpClientSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return HttpClientSymbolProcessor(environment)
    }
}
