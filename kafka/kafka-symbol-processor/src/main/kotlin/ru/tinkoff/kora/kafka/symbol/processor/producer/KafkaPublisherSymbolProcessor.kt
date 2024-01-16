package ru.tinkoff.kora.kafka.symbol.processor.producer

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonAopUtils
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.generatedClassName

class KafkaPublisherSymbolProcessor(val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val publisherGenerator = KafkaPublisherGenerator(env, resolver)
        val publisherTransactionalGenerator = KafkaPublisherTransactionalGenerator(env, resolver)

        val producers = mutableListOf<KSAnnotated>()
        val deferred = mutableListOf<KSAnnotated>()
        for (it in resolver.getSymbolsWithAnnotation(KafkaClassNames.kafkaPublisherAnnotation.canonicalName)) {
            if (it.validate()) {
                producers.add(it)
            } else {
                deferred.add(it)
            }
        }

        // generate publisher module with every @AopProxy annotated class
        // that has @KafkaPublisher annotated interface as grandparent
        // i.e. capture generated aspects for generated publishers
        // from previous rounds and make them visible for graph
        for (aopProxy in resolver.getSymbolsWithAnnotation(CommonClassNames.aopProxy.canonicalName)) {
            if (aopProxy is KSClassDeclaration) {
                val proxySupertype = aopProxy.superTypes.firstOrNull() ?: continue
                val proxySupertypeDecl = proxySupertype.resolve().declaration
                if (proxySupertypeDecl !is KSClassDeclaration) continue
                for (publisher in proxySupertypeDecl.superTypes) {
                    val publisherDeclaration = publisher.resolve().declaration as KSClassDeclaration
                    if (CommonAopUtils.hasAopAnnotations(publisherDeclaration)) {
                        val annotation = publisherDeclaration.findAnnotation(KafkaClassNames.kafkaPublisherAnnotation)
                            ?: continue
                        val publishMethods = publisherDeclaration.getAllFunctions()
                            .filter { it.findOverridee()?.parentDeclaration?.qualifiedName?.asString() != "kotlin.Any" }
                            .toList()

                        val topicConfig = if (publishMethods.any { it.isAnnotationPresent(KafkaClassNames.kafkaTopicAnnotation) }) {
                            ClassName(publisherDeclaration.packageName.asString(), publisherDeclaration.generatedClassName("TopicConfig"))
                        } else {
                            null
                        }
                        publisherGenerator.generatePublisherModule(publisherDeclaration, publishMethods, annotation, topicConfig, aopProxy)

                    }
                }

            }
        }


        for (producer in producers) {
            if (producer !is KSClassDeclaration || producer.classKind != ClassKind.INTERFACE) {
                env.logger.error("@KafkaPublisher can be placed only on interfaces", producer)
                continue
            }
            try {
                val annotation = producer.findAnnotation(KafkaClassNames.kafkaPublisherAnnotation)!!
                val supertypes = producer.superTypes.filter { it.toTypeName() != ANY }.toList()
                if (supertypes.isEmpty()) {
                    val publishMethods = producer.getAllFunctions()
                        .filter { it.findOverridee()?.parentDeclaration?.qualifiedName?.asString() != "kotlin.Any" }
                        .toList()
                    val topicConfig = publisherGenerator.generateConfig(producer, publishMethods)
                    publisherGenerator.generatePublisherImpl(producer, publishMethods, topicConfig)

                    // we'll generate module after aop proxy generated
                    if (!CommonAopUtils.hasAopAnnotations(producer)) {
                        publisherGenerator.generatePublisherModule(producer, publishMethods, annotation, topicConfig, null)
                    }
                    continue
                }
                if (supertypes.size > 1) {
                    env.logger.error("@KafkaPublisher can be placed only on interfaces extending only TransactionalPublisher or none", producer)
                    continue
                }
                val supertype = supertypes.first()
                val supertypeName = supertype.toTypeName()
                if (supertypeName !is ParameterizedTypeName) {
                    env.logger.error("@KafkaPublisher can be placed only on interfaces extending only TransactionalPublisher or none", producer)
                    continue
                }
                if (supertypeName.rawType != KafkaClassNames.transactionalPublisher) {
                    env.logger.error("@KafkaPublisher can be placed only on interfaces extending only TransactionalPublisher or none", producer)
                    continue
                }
                val publisherType = supertype.resolve().arguments.first().type!!.resolve()
                val publisherDeclaration = publisherType.declaration as KSClassDeclaration
                val publisherAnnotation = publisherDeclaration.findAnnotation(KafkaClassNames.kafkaPublisherAnnotation)
                if (publisherAnnotation == null) {
                    env.logger.error("TransactionalPublisher can only have argument types that annotated with @KafkaPublisher too", producer)
                    continue
                }
                val publisherTypeName = publisherDeclaration.toClassName()
                publisherTransactionalGenerator.generatePublisherTransactionalImpl(producer, publisherTypeName, publisherDeclaration)
                publisherTransactionalGenerator.generatePublisherTransactionalModule(producer, publisherDeclaration, publisherAnnotation)
            } catch (e: ProcessingErrorException) {
                e.printError(env.logger)
            }


        }

        return deferred
    }
}
