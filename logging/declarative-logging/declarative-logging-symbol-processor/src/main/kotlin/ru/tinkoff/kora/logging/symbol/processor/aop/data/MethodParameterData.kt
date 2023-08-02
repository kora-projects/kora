package ru.tinkoff.kora.logging.symbol.processor.aop.data

import org.slf4j.event.Level

data class MethodParameterData(
    val name: String,
    val logLevel: Level?
)
