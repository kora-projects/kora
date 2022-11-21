package ru.tinkoff.kora.config.symbol.processor.cases

import java.util.*

data class DataClassConfig(
    val intField: Int,
    val boxedIntField: Int,
    val longField: Long,
    val boxedLongField: Long,
    val doubleField: Double,
    val boxedDoubleField: Double,
    val booleanField: Boolean,
    val boxedBooleanField: Boolean,
    val stringField: String,
    val listField: List<Int>,
    val objectField: SomeConfig,
    val props: Properties
)
