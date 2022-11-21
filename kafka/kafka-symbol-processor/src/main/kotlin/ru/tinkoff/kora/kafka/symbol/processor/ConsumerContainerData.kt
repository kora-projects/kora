package ru.tinkoff.kora.kafka.symbol.processor

import com.squareup.kotlinpoet.TypeName

data class ConsumerContainerData(val keyType: TypeName, val valueType: TypeName)
