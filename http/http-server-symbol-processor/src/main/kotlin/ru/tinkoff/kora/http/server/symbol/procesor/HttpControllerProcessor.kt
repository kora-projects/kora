package ru.tinkoff.kora.http.server.symbol.procesor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.visitClass

class HttpControllerProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val routeProcessor = RouteProcessor()

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(HttpServerClassNames.httpController.canonicalName)
        val unableToProcess = symbols.filterNot { it.validate() }.toList()
        for (symbol in symbols.filter { it.validate() }) {
            symbol.visitClass { declaration ->
                try {
                    processController(declaration)
                } catch (e: ProcessingErrorException) {
                    e.printError(kspLogger)
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }
        return unableToProcess
    }

    private fun processController(declaration: KSClassDeclaration) {
        val packageName = declaration.packageName.asString()
        val moduleName = "${declaration.toClassName().simpleName}Module"
        val moduleBuilder = TypeSpec.interfaceBuilder(moduleName)
            .generated(HttpControllerProcessor::class)
            .addAnnotation(CommonClassNames.module)
            .addOriginatingKSFile(declaration)

        val fileSpec = FileSpec.builder(
            packageName = packageName,
            fileName = moduleName
        )

        val rootPath = declaration.findAnnotation(HttpServerClassNames.httpController)!!
            .findValueNoDefault<String>("value")
            ?.trim()
            ?: ""
        val routes = declaration.getAllFunctions().filter { it.isAnnotationPresent(HttpServerClassNames.httpRoute) }
        routes.forEach { function ->
            val funBuilder = routeProcessor.buildHttpRouteFunction(declaration, rootPath, function)
            moduleBuilder.addFunction(funBuilder.build())
        }
        fileSpec.addType(moduleBuilder.build()).build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }
}
