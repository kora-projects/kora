package ru.tinkoff.kora.kafka.symbol.processor.consumer

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.isAnyException
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.isConsumer
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.isConsumerRecord
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.isConsumerRecords
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.isKeyDeserializationException
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.isRecordsTelemetry
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.isValueDeserializationException

sealed interface ConsumerParameter {
    val parameter: KSValueParameter

    data class Consumer(override val parameter: KSValueParameter, val key: KSType, val value: KSType) : ConsumerParameter

    data class Records(override val parameter: KSValueParameter, val key: KSType, val value: KSType) : ConsumerParameter

    data class Exception(override val parameter: KSValueParameter) : ConsumerParameter

    data class KeyDeserializationException(override val parameter: KSValueParameter) : ConsumerParameter

    data class ValueDeserializationException(override val parameter: KSValueParameter) : ConsumerParameter

    data class Record(override val parameter: KSValueParameter, val key: KSType, val value: KSType) : ConsumerParameter

    data class RecordsTelemetry(override val parameter: KSValueParameter, val key: KSType, val value: KSType) : ConsumerParameter

    data class Unknown(override val parameter: KSValueParameter) : ConsumerParameter

    companion object {
        fun parseParameters(function: KSFunctionDeclaration) = function.parameters.map {
            val type = it.type.resolve()
            when {
                type.isConsumerRecord() -> Record(it, type.arguments[0].type!!.resolve(), type.arguments[1].type!!.resolve())
                type.isConsumerRecords() -> Records(it, type.arguments[0].type!!.resolve(), type.arguments[1].type!!.resolve())
                type.isConsumer() -> Consumer(it, type.arguments[0].type!!.resolve(), type.arguments[1].type!!.resolve())
                type.isRecordsTelemetry() -> RecordsTelemetry(it, type.arguments[0].type!!.resolve(), type.arguments[1].type!!.resolve())
                type.isKeyDeserializationException() -> KeyDeserializationException(it)
                type.isValueDeserializationException() -> ValueDeserializationException(it)
                type.isAnyException() -> Exception(it)
                else -> Unknown(it)
            }
        }
    }
}
