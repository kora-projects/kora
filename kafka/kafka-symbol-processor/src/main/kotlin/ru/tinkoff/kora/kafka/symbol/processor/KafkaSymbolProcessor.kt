package ru.tinkoff.kora.kafka.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.kafka.common.annotation.KafkaIncoming
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.visitFunction

@KspExperimental
class KafkaSymbolProcessor(private val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    lateinit var resolver: Resolver
    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        this.resolver = resolver
        val controllers = hashSetOf<KSClassDeclaration>()
        resolver.getSymbolsWithAnnotation(KafkaIncoming::class.qualifiedName!!).forEach {
            it.visitFunction { ksFunctionDeclaration ->
                controllers.add(ksFunctionDeclaration.parentDeclaration as KSClassDeclaration)
            }
        }
        controllers.forEach { ksClassDeclaration ->
            processController(ksClassDeclaration)
        }
        return emptyList()
    }

    private fun processController(controller: KSClassDeclaration) {
        val methodGenerator = KafkaConsumerGenerator(environment.logger, resolver)
        val kafkaConfigGenerator = KafkaConfigGenerator(resolver)
        val generator = KafkaModuleGenerator(methodGenerator, kafkaConfigGenerator)
        val file = generator.generateModule(controller)
        file.writeTo(environment.codeGenerator, false)
    }
}

@KspExperimental
class KafkaSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return KafkaSymbolProcessor(environment)
    }
}
