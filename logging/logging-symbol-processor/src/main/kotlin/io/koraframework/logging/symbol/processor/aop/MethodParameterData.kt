package io.koraframework.logging.symbol.processor.aop

import org.slf4j.event.Level

data class MethodParameterData(
    val name: String,
    val logLevel: Level?
)
