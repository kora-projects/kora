package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.http.client.common.annotation.HttpClient
import ru.tinkoff.kora.kora.app.ksp.isClassExists
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.visitClass

@KspExperimental
class HttpClientSymbolProcessor(val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    private lateinit var clientGenerator: ClientClassGenerator
    private lateinit var configGenerator: ConfigClassGenerator
    private lateinit var configModuleGenerator: ConfigModuleGenerator

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        clientGenerator = ClientClassGenerator(resolver)
        configGenerator = ConfigClassGenerator()
        configModuleGenerator = ConfigModuleGenerator(resolver)

        val symbols = resolver.getSymbolsWithAnnotation(HttpClient::class.qualifiedName!!).toList()
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
        if (!isClassExists(resolver, packageName + "." + config.name)) {
            val fileSpec = FileSpec.get(packageName, config)
            fileSpec.writeTo(environment.codeGenerator, false)
        }
    }
}

@KspExperimental
class HttpClientSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return HttpClientSymbolProcessor(environment)
    }
}
