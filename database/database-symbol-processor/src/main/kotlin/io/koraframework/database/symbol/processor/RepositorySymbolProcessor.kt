package io.koraframework.database.symbol.processor

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.visitClass

class RepositorySymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private lateinit var repositoryBuilder: RepositoryBuilder

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        repositoryBuilder = RepositoryBuilder(resolver, kspLogger)

        for (annotatedClass in resolver.getSymbolsWithAnnotation(DbUtils.repositoryAnnotation.canonicalName)) {
            try {
                annotatedClass.visitClass { this.processClass(it) }
            } catch (e: ProcessingErrorException) {
                e.printError(kspLogger)
            }
        }
        return emptyList()
    }

    private fun processClass(declaration: KSClassDeclaration) {
        if (declaration.classKind != ClassKind.INTERFACE && !(declaration.classKind == ClassKind.CLASS && declaration.isAbstract())) {
            throw ProcessingErrorException(
                listOf(
                    ProcessingError(
                        "@Repository is only applicable to interfaces and abstract classes",
                        declaration
                    )
                )
            )
        }
        val typeSpec = repositoryBuilder.build(declaration) ?: return
        val fileSpec = FileSpec.builder(declaration.packageName.asString(), typeSpec.name!!).addType(typeSpec)
        fileSpec.build().writeTo(codeGenerator, false)
    }
}

class RepositorySymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RepositorySymbolProcessor(environment)
    }
}


