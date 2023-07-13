package ru.tinkoff.kora.config.ksp.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import ru.tinkoff.kora.config.ksp.ConfigClassNames
import ru.tinkoff.kora.config.ksp.ConfigParserGenerator

class ConfigParserSymbolProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val configParserGenerator = ConfigParserGenerator(resolver)
        val seen = HashSet<String>()
        val elements = resolver.getSymbolsWithAnnotation(ConfigClassNames.configValueExtractorAnnotation.canonicalName)
            .plus(resolver.getSymbolsWithAnnotation(ConfigClassNames.configSourceAnnotation.canonicalName))
            .filterIsInstance<KSClassDeclaration>()


        for (element in elements) {
            if (!seen.add(element.qualifiedName!!.asString())) {
                continue
            }
            if (element.classKind == ClassKind.INTERFACE) {
                val result = configParserGenerator.generateForInterface(environment.codeGenerator, element.asType(listOf()), false)
                if (result.isRight) {
                    for (processingError in result.right()!!) {
                        processingError.print(environment.logger)
                    }
                }

            } else if (element.classKind == ClassKind.CLASS && element.modifiers.contains(Modifier.DATA)){
                val result = configParserGenerator.generateForDataClass(environment.codeGenerator, element.asType(listOf()), false)
                if (result.isRight) {
                    for (processingError in result.right()!!) {
                        processingError.print(environment.logger)
                    }
                }
            } else {
                environment.logger.error("@${ConfigClassNames.configValueExtractorAnnotation.simpleName} is applicable only to data classes or interfaces")
            }
        }
        return emptyList()
    }
}


class ConfigParserSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ConfigParserSymbolProcessor(environment)
    }
}
