package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.util.*

@Json
data class DtoWithSupportedTypes(
    val string: String?,
    val boolean: Boolean,
    val integer: Int,
    val bigInteger: BigInteger,
    val bigDecimal: BigDecimal,
    val double: Double,
    val float: Float,
    val long: Long,
    val short: Short,
    val binary: ByteArray,
    val listOfInteger: List<Int>,
    val setOfInteger: Set<Int>
)
