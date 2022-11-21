package ru.tinkoff.kora.config.symbol.processor.cases

import ru.tinkoff.kora.config.common.ConfigSource
import java.util.*

@ConfigSource("some.path")
data class ConfigWithConfigSource(
    val field1: String,
    val field2: Int,
    val field3: Boolean
)
