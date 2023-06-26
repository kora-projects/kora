package ru.tinkoff.kora.kafka.symbol.processor.consumer

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.visitFunction

class KafkaListenerSymbolProcessor(private val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    lateinit var resolver: Resolver

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        this.resolver = resolver
        val controllers = hashSetOf<KSClassDeclaration>()
        for (it in resolver.getSymbolsWithAnnotation(KafkaClassNames.kafkaListener.canonicalName)) {
            it.visitFunction { ksFunctionDeclaration ->
                controllers.add(ksFunctionDeclaration.parentDeclaration as KSClassDeclaration)
            }
        }
        for (ksClassDeclaration in controllers) {
            try {
                processController(ksClassDeclaration)
            } catch (e: ProcessingErrorException) {
                e.printError(kspLogger)
            }
        }
        return emptyList()
    }

    private fun processController(controller: KSClassDeclaration) {
        val methodGenerator = KafkaHandlerGenerator(environment.logger, resolver)
        val kafkaConfigGenerator = KafkaConsumerConfigGenerator()
        val kafkaContainerGenerator = KafkaContainerGenerator()
        val generator = KafkaConsumerModuleGenerator(methodGenerator, kafkaConfigGenerator, kafkaContainerGenerator)
        val file = generator.generateModule(controller)
        file.writeTo(environment.codeGenerator, false)
    }
}
