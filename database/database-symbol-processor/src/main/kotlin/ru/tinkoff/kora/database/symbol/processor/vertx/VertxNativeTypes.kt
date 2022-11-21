package ru.tinkoff.kora.database.symbol.processor.vertx

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import ru.tinkoff.kora.database.symbol.processor.model.QueryResult
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime

object VertxNativeTypes {
    interface VertxNativeType {
        fun type(): TypeName
        fun extract(rowName: String, indexName: String): CodeBlock
    }

    fun nativeType(type: TypeName, extractor: (String, String) -> CodeBlock): VertxNativeType {
        return object : VertxNativeType {
            override fun type() = type

            override fun extract(rowName: String, indexName: String) = extractor.invoke(rowName, indexName)
        }
    }

    private val nativeTypes = listOf(
        nativeType(Boolean::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getBoolean(%L)", row, idx) }),
        nativeType(Boolean::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getBoolean(%L)", row, idx) }),
        nativeType(Int::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getInteger(%L)", row, idx) }),
        nativeType(Int::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getInteger(%L)", row, idx) }),
        nativeType(Long::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getLong(%L)", row, idx) }),
        nativeType(Long::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getLong(%L)", row, idx) }),
        nativeType(Double::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getDouble(%L)", row, idx) }),
        nativeType(Double::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getDouble(%L)", row, idx) }),
        nativeType(String::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getString(%L)", row, idx) }),
        nativeType(String::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getString(%L)", row, idx) }),
        nativeType(BigDecimal::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getNumeric(%L).bigDecimalValue()", row, idx) }),
        nativeType(BigDecimal::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getNumeric(%L).bigDecimalValue()", row, idx) }),
        nativeType(BigInteger::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getNumeric(%L).bigIntegerValue()", row, idx) }),
        nativeType(BigInteger::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getNumeric(%L).bigIntegerValue()", row, idx) }),
        nativeType(LocalDateTime::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getLocalDateTime(%L)", row, idx) }),
        nativeType(LocalDateTime::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getLocalDateTime(%L)", row, idx) }),
        nativeType(LocalDate::class.asTypeName(), { row, idx -> CodeBlock.of("%L.getLocalDate(%L)", row, idx) }),
        nativeType(LocalDate::class.asTypeName().copy(true), { row, idx -> CodeBlock.of("%L.getLocalDate(%L)", row, idx) }),
        nativeType(ClassName("io.vertx.core.buffer", "Buffer"), { row, idx -> CodeBlock.of("%L.getBuffer(%L)", row, idx) }),
        nativeType(ClassName("io.vertx.core.buffer", "Buffer").copy(true), { row, idx -> CodeBlock.of("%L.getBuffer(%L)", row, idx) }),
    )

    fun findNativeType(type: TypeName) = nativeTypes.firstOrNull { it.type() == type }

    fun extract(simpleResult: QueryResult.SimpleResult, index: CodeBlock): CodeBlock {

        throw IllegalStateException("Invalid type " + simpleResult.type.declaration.simpleName.asString())
    }
}
