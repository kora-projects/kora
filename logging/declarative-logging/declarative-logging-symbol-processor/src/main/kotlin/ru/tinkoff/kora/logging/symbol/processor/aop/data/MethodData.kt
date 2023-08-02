package ru.tinkoff.kora.logging.symbol.processor.aop.data

import org.slf4j.event.Level

data class MethodData(
    val superCall: String,
    val loggerName: String,
    val inputLogLevel: Level?,
    val outputLogLevel: Level?,
    val resultLogLevel: Level?,
    val parameters: List<MethodParameterData>,
)
