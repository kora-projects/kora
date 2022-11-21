package ru.tinkoff.kora.kafka.symbol.processor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.serialization.Deserializer
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.kafka.common.annotation.KafkaIncoming
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig
import ru.tinkoff.kora.kafka.common.containers.KafkaConsumerContainer
import ru.tinkoff.kora.kafka.common.containers.handlers.wrapper.HandlerWrapper
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

@KspExperimental
class KafkaConsumerGenerator(private val kspLogger: KSPLogger, resolver: Resolver) {
    private val nullableExceptionType = resolver.getClassDeclarationByName(java.lang.Exception::class.qualifiedName!!)!!.asType(emptyList()).makeNullable()
    private val consumerType = resolver.getClassDeclarationByName(Consumer::class.qualifiedName!!)!!.asStarProjectedType()
    private val consumerRecordType = resolver.getClassDeclarationByName(ConsumerRecord::class.qualifiedName!!)!!.asStarProjectedType()
    private val consumerRecordsType = resolver.getClassDeclarationByName(ConsumerRecords::class.qualifiedName!!)!!.asStarProjectedType()

    fun generate(functionDeclaration: KSFunctionDeclaration): FunSpec? {
        functionDeclaration.getAnnotationsByType(KafkaIncoming::class).firstOrNull() ?: return null
        val controller = functionDeclaration.parentDeclaration as KSClassDeclaration
        val methodName = prepareMethodName(controller.simpleName.asString(), functionDeclaration.simpleName.asString())
        val funBuilder = FunSpec.builder(methodName)
            .addParameter("_controller", controller.toClassName())
        val tagName = prepareTagName(controller.simpleName.asString(), functionDeclaration.simpleName.asString())
        val tagsBlock = CodeBlock.builder().add("%L::class", tagName)
        val propertiesParameter = ParameterSpec.builder("_consumerConfig", KafkaConsumerConfig::class)
        propertiesParameter.addAnnotation(
            AnnotationSpec.builder(Tag::class).addMember(tagsBlock.build()).build()
        )
        funBuilder.addParameter(propertiesParameter.build())
        val consumerData = extractConsumerData(functionDeclaration)

        funBuilder.addParameter(
            "keyDeserializer", Deserializer::class.asClassName().parameterizedBy(consumerData.keyType)
        )
        funBuilder.addParameter(
            "valueDeserializer", Deserializer::class.asClassName().parameterizedBy(consumerData.valueType)
        )
        funBuilder.addParameter(
            "telemetry", KafkaConsumerTelemetry::class.asClassName().parameterizedBy(consumerData.keyType, consumerData.valueType)
        )
        funBuilder.returns(
            KafkaConsumerContainer::class.asClassName().parameterizedBy(consumerData.keyType, consumerData.valueType)
        )
        val returnBlock = CodeBlock.of(
            """
                    return %T(
                            _consumerConfig,
                            keyDeserializer,
                            valueDeserializer,
                            %T.wrapHandler(telemetry, _controller::%L)
                )""",
            KafkaConsumerContainer::class,
            HandlerWrapper::class,
            functionDeclaration.simpleName.asString()
        )
        funBuilder.addCode(returnBlock)
        return funBuilder.build()
    }

    private fun extractConsumerData(ksFunctionDeclaration: KSFunctionDeclaration): ConsumerContainerData {
        val params = ksFunctionDeclaration.parameters
        val isSuspend = ksFunctionDeclaration.modifiers.contains(Modifier.SUSPEND)
        if (isSuspend) {
            throw ProcessingErrorException("Unsupported signature for @KafkaIncoming: suspend function", ksFunctionDeclaration)
        }

        when(params.size) {
            3 -> {
                if (params[2].type.resolve() != nullableExceptionType) {
                    throw ProcessingErrorException("Unsupported signature for @KafkaIncoming: 3rd argument should be 'Exception?'", ksFunctionDeclaration)
                }

                return ConsumerContainerData(
                    params[0].type.toTypeName(),
                    params[1].type.toTypeName()
                )
            }

            2 -> {
                val firstParamType = params[0].type.resolve()
                val secondParamType = params[1].type.resolve()

                val firstParamTypeProjection = params[0].type.resolve().starProjection()
                if ((firstParamTypeProjection == consumerRecordType || firstParamTypeProjection == consumerRecordsType)) {
                    if (secondParamType.starProjection() != consumerType) {
                        throw ProcessingErrorException("Second argument should have type org.apache.kafka.clients.consumer.Consumer", ksFunctionDeclaration);
                    }

                    return ConsumerContainerData(
                        firstParamType.arguments[0].toTypeName(),
                        firstParamType.arguments[1].toTypeName()
                    )
                }

                if (secondParamType == nullableExceptionType) {
                    return ConsumerContainerData(
                        ByteArray::class.asTypeName(),
                        params[0].type.toTypeName()
                    )
                }


                return ConsumerContainerData(
                    params[0].type.toTypeName(),
                    params[1].type.toTypeName()
                )
            }

            1 -> {
                val firstParamType = params[0].type.resolve()
                val firstParamTypeProjection = params[0].type.resolve().starProjection()

                if ((firstParamTypeProjection == consumerRecordType || firstParamTypeProjection == consumerRecordsType)) {
                    return ConsumerContainerData(
                        firstParamType.arguments[0].toTypeName(),
                        firstParamType.arguments[1].toTypeName()
                    )
                }

                return ConsumerContainerData(
                    ByteArray::class.asTypeName(),
                    params[0].type.toTypeName()
                )
            }
        }

        throw ProcessingErrorException(ProcessingError("Unsupported signature for @KafkaIncoming", ksFunctionDeclaration))
    }

}
