package ru.tinkoff.kora.soap.client.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.visitClass
import java.io.IOException

class WebServiceClientSymbolProcessor(private val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    private fun processService(service: KSClassDeclaration, soapClasses: SoapClasses, generator: SoapClientImplGenerator) {
        val typeSpec = generator.generate(service, soapClasses)
        val typeFileSpec = FileSpec.get(service.packageName.asString(), typeSpec)
        typeFileSpec.writeTo(env.codeGenerator, true)

        val moduleSpec = generator.generateModule(service, soapClasses)
        val moduleFileSpec = FileSpec.get(service.packageName.asString(), moduleSpec)
        moduleFileSpec.writeTo(env.codeGenerator, true)
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val jakartaWebService = resolver.getClassDeclarationByName("jakarta.jws.WebService")
        val javaxWebService = resolver.getClassDeclarationByName("javax.jws.WebService")
        val generator = SoapClientImplGenerator(resolver)
        if (jakartaWebService != null) {
            val symbols = resolver.getSymbolsWithAnnotation("jakarta.jws.WebService").toList()
            symbols.forEach {
                it.visitClass { declaration ->
                    try {
                        processService(declaration, SoapClasses.JakartaClasses, generator)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
        if (javaxWebService != null) {
            val symbols = resolver.getSymbolsWithAnnotation("javax.jws.WebService").toList()
            symbols.forEach {
                it.visitClass { declaration ->
                    try {
                        processService(declaration, SoapClasses.JavaxClasses, generator)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
        return listOf()
    }
}

