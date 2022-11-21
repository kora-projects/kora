package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.LocalDateTime

object CassandraNativeTypes {
    private val nativeTypes = listOf(
        CassandraNativeType.of(
            Boolean::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getBoolean(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setBoolean(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            Boolean::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getBoolean(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setBoolean(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            Int::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getInt(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setInt(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            Int::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getInt(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setInt(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            Long::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getLong(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setLong(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            Long::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getLong(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setLong(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            Double::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getDouble(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setDouble(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            Double::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getDouble(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setDouble(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            String::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getString(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setString(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            String::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getString(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setString(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            BigDecimal::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getBigDecimal(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setBigDecimal(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            BigDecimal::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getBigDecimal(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setBigDecimal(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            ByteBuffer::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getByteBuffer(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setByteBuffer(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            ByteBuffer::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getByteBuffer(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setByteBuffer(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            LocalDate::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getLocalDate(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setLocalDate(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            LocalDate::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getLocalDate(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setLocalDate(%L, %N)", stmt, idx, variableName) },
        ),
        CassandraNativeType.of(
            LocalDateTime::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.get(%L, %T::class.java)", rsName, i, LocalDateTime::class) },
            { stmt, variableName, idx -> CodeBlock.of("%N.set(%L, %N, %T::class.java)", stmt, idx, variableName, LocalDateTime::class) },
        ),
        CassandraNativeType.of(
            LocalDateTime::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.get(%L, %T::class.java)", rsName, i, LocalDateTime::class) },
            { stmt, variableName, idx -> CodeBlock.of("%N.set(%L, %N, %T::class.java)", stmt, idx, variableName, LocalDateTime::class) },
        ),
    )

    fun findNativeType(typeName: TypeName): CassandraNativeType? {
        for (nativeParameterType in nativeTypes) {
            if (nativeParameterType.type() == typeName) {
                return nativeParameterType
            }
        }
        return null
    }
}
