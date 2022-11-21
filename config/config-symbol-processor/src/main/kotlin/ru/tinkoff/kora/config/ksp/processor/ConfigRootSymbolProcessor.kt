package ru.tinkoff.kora.config.ksp.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.config.common.ConfigRoot
import ru.tinkoff.kora.config.ksp.ConfigParserGenerator
import ru.tinkoff.kora.config.ksp.ConfigRootModuleGenerator
import ru.tinkoff.kora.config.ksp.exception.NewRoundWantedException
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.visitClass
import java.io.IOException

@KspExperimental
class ConfigRootSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private lateinit var moduleGenerator: ConfigRootModuleGenerator
    private lateinit var configParserGenerator: ConfigParserGenerator

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        moduleGenerator = ConfigRootModuleGenerator(resolver)
        configParserGenerator = ConfigParserGenerator(resolver)
        val unprocessableSymbols = mutableListOf<KSClassDeclaration>()
        val symbolsToProcess = resolver.getSymbolsWithAnnotation(ConfigRoot::class.qualifiedName!!).filter { it.validate() }
        symbolsToProcess.forEach {
            it.visitClass { config ->
                try {
                    val configParser = configParserGenerator.generate(config)
                    configParser.writeTo(codeGenerator, false)
                    val module = moduleGenerator.generateModule(config)
                    module.writeTo(codeGenerator, false)
                } catch (e: NewRoundWantedException) {
                    unprocessableSymbols.add(e.declaration)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }
        return unprocessableSymbols
    }
}

@KspExperimental
class ConfigRootSymbolProcessorProvider: SymbolProcessorProvider{
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ConfigRootSymbolProcessor(environment)
    }
}
