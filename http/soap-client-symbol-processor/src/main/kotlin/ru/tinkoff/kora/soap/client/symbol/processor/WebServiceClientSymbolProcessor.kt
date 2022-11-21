package ru.tinkoff.kora.soap.client.symbol.processor

import com.google.devtools.ksp.KspExperimental
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

@KspExperimental
class WebServiceClientSymbolProcessor(private val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    private fun processService(service: KSClassDeclaration, soapClasses: SoapClasses, generator: SoapClientImplGenerator) {
        val typeSpec = generator.generate(service, soapClasses)
        val fileSpec = FileSpec.get(service.packageName.asString(), typeSpec)
        fileSpec.writeTo(env.codeGenerator, true)
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val jakartaWebService = resolver.getClassDeclarationByName("jakarta.jws.WebService")
        val javaxWebService = resolver.getClassDeclarationByName("javax.jws.WebService")
        val generator = SoapClientImplGenerator(resolver)
        if (jakartaWebService != null) {
            val jakartaClasses = SoapClasses.JakartaClasses(resolver)
            val symbols = resolver.getSymbolsWithAnnotation("jakarta.jws.WebService").toList()
            symbols.forEach {
                it.visitClass { declaration ->
                    try {
                        processService(declaration, jakartaClasses, generator)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
        if (javaxWebService != null) {
            val javaxClasses = SoapClasses.JavaxClasses(resolver)
            val symbols = resolver.getSymbolsWithAnnotation("javax.jws.WebService").toList()
            symbols.forEach {
                it.visitClass { declaration ->
                    try {
                        processService(declaration, javaxClasses, generator)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
        return listOf()
    }
}

