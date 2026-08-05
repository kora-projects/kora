package io.koraframework.kafka.symbol.processor.consumer

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.koraframework.ksp.common.exception.ProcessingErrorException
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import io.koraframework.kafka.symbol.processor.KafkaUtils.isAnyException
import io.koraframework.kafka.symbol.processor.KafkaUtils.isConsumer
import io.koraframework.kafka.symbol.processor.KafkaUtils.isConsumerRecord
import io.koraframework.kafka.symbol.processor.KafkaUtils.isConsumerRecords
import io.koraframework.kafka.symbol.processor.KafkaUtils.isKeyDeserializationException
import io.koraframework.kafka.symbol.processor.KafkaUtils.isValueDeserializationException

sealed interface ConsumerParameter {
    val parameter: KSValueParameter

    data class Consumer(override val parameter: KSValueParameter, val key: KSType?, val value: KSType?) : ConsumerParameter

    data class Records(override val parameter: KSValueParameter, val key: KSType?, val value: KSType?) : ConsumerParameter

    data class Exception(override val parameter: KSValueParameter) : ConsumerParameter

    data class KeyDeserializationException(override val parameter: KSValueParameter) : ConsumerParameter

    data class ValueDeserializationException(override val parameter: KSValueParameter) : ConsumerParameter

    data class Record(override val parameter: KSValueParameter, val key: KSType?, val value: KSType?) : ConsumerParameter

    data class RecordsTelemetry(override val parameter: KSValueParameter, val key: KSType?, val value: KSType?) : ConsumerParameter

    data class Unknown(override val parameter: KSValueParameter) : ConsumerParameter

    companion object {
        fun parseParameters(function: KSFunctionDeclaration) = function.parameters.map {
            val type = it.type.resolve()
            if (type.isError) {
                // KotlinPoet refuses to render an error type, and every check below renders one, so
                // an unresolvable parameter used to take KSP down instead of naming the parameter
                throw ProcessingErrorException(
                    """
                    Kafka listener parameter type cannot be resolved:
                      parameter: ${it.name?.asString()}
                      type: ${it.type}

                    Fix:
                      - Check imports and module dependencies.
                      - Compile without Kora symbol processors to expose earlier Kotlin errors if KSP hides them.
                    """.trimIndent(),
                    it
                )
            }
            when {
                type.isConsumerRecord() -> Record(it, type.arguments[0].type?.resolve(), type.arguments[1].type?.resolve())
                type.isConsumerRecords() -> Records(it, type.arguments[0].type?.resolve(), type.arguments[1].type?.resolve())
                type.isConsumer() -> Consumer(it, type.arguments[0].type?.resolve(), type.arguments[1].type?.resolve())
                type.isKeyDeserializationException() -> KeyDeserializationException(it)
                type.isValueDeserializationException() -> ValueDeserializationException(it)
                type.isAnyException() -> Exception(it)
                else -> Unknown(it)
            }
        }
    }
}
