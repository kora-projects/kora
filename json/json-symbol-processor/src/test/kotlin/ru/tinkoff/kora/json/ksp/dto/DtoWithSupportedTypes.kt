package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json
import java.math.BigInteger

@Json
data class DtoWithSupportedTypes(
    val string: String?,
    val boolean: Boolean,
    val integer: Int,
    val bigInteger: BigInteger,
    val double: Double,
    val float: Float,
    val long: Long,
    val short: Short,
    val binary: ByteArray,
    val listOfInteger: List<Int>,
    val setOfInteger: Set<Int>
)
