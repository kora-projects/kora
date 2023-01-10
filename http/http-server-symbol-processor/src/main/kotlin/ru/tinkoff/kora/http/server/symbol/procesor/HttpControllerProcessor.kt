package ru.tinkoff.kora.http.server.symbol.procesor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import ru.tinkoff.kora.http.server.symbol.procesor.exception.HttpProcessorException
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.visitClass

@KspExperimental
class HttpControllerProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    private lateinit var routeProcessor: RouteProcessor
    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        routeProcessor = RouteProcessor(resolver)
        val symbols = resolver.getSymbolsWithAnnotation(HttpController::class.qualifiedName.toString())
        val unableToProcess = symbols.filterNot { it.validate() }.toList()
        for (symbol in symbols.filter { it.validate() }) {
            symbol.visitClass { declaration ->
                try {
                    processController(declaration)
                } catch (e: HttpProcessorException) {
                    e.printError(kspLogger)
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }
        return unableToProcess
    }

    private fun processController(declaration: KSClassDeclaration) {
        val containingFile = declaration.containingFile!!
        val packageName = containingFile.packageName.asString()
        val moduleName = "${declaration.toClassName().simpleName}Module"
        val moduleBuilder = TypeSpec.interfaceBuilder(moduleName)
            .addAnnotation(CommonClassNames.module)
            .generated(HttpControllerProcessor::class)
            .addOriginatingKSFile(containingFile)
        val fileSpec = FileSpec.builder(
            packageName = packageName,
            fileName = moduleName
        )
        val rootPath = declaration.getAnnotationsByType(HttpController::class).first().value
        val routes = declaration.getAllFunctions().filter { it.isAnnotationPresent(HttpRoute::class) }
        routes.forEach { function ->
            val funBuilder = routeProcessor.buildHttpRouteFunction(declaration, rootPath, function)
            moduleBuilder.addFunction(funBuilder.build())
        }
        fileSpec.addType(moduleBuilder.build()).build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }
}

@KspExperimental
class HttpControllerProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return HttpControllerProcessor(environment)
    }
}
