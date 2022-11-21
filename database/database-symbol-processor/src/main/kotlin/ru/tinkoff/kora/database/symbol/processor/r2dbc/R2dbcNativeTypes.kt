package ru.tinkoff.kora.database.symbol.processor.r2dbc

import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime

object R2dbcNativeTypes {
    val types = listOf(
        R2dbcNativeType.of(Boolean::class.asTypeName()),
        R2dbcNativeType.of(Boolean::class.asTypeName().copy(true)),
        R2dbcNativeType.of(Int::class.asTypeName()),
        R2dbcNativeType.of(Int::class.asTypeName().copy(true)),
        R2dbcNativeType.of(Long::class.asTypeName()),
        R2dbcNativeType.of(Long::class.asTypeName().copy(true)),
        R2dbcNativeType.of(Double::class.asTypeName()),
        R2dbcNativeType.of(Double::class.asTypeName().copy(true)),
        R2dbcNativeType.of(String::class.asTypeName()),
        R2dbcNativeType.of(String::class.asTypeName().copy(true)),
        R2dbcNativeType.of(BigDecimal::class.asTypeName()),
        R2dbcNativeType.of(BigDecimal::class.asTypeName().copy(true)),
        R2dbcNativeType.of(BigInteger::class.asTypeName()),
        R2dbcNativeType.of(BigInteger::class.asTypeName().copy(true)),
        R2dbcNativeType.of(LocalDateTime::class.asTypeName()),
        R2dbcNativeType.of(LocalDateTime::class.asTypeName().copy(true)),
        R2dbcNativeType.of(LocalDate::class.asTypeName()),
        R2dbcNativeType.of(LocalDate::class.asTypeName().copy(true)),
        R2dbcNativeType.of(ByteArray::class.asTypeName()),
        R2dbcNativeType.of(ByteArray::class.asTypeName().copy(true)),
    )


    fun findNativeType(typeName: TypeName) = types.firstOrNull { it.type() == typeName }
}
