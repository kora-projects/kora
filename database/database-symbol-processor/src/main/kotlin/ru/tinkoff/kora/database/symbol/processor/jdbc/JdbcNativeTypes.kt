package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import java.math.BigDecimal
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime

object JdbcNativeTypes {
    private val types = listOf(
        JdbcNativeType.of(
            Boolean::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getBoolean(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setBoolean(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.BOOLEAN)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            Boolean::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getBoolean(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setBoolean(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.BOOLEAN)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            Int::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getInt(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setInt(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.INTEGER)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            Int::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getInt(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setInt(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.INTEGER)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            Long::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getLong(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setLong(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.BIGINT)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            Long::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getLong(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setLong(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.BIGINT)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            Double::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getDouble(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setDouble(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.DOUBLE)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            Double::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getDouble(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setDouble(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.DOUBLE)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            String::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getString(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setString(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.VARCHAR)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            String::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getString(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setString(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.VARCHAR)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            BigDecimal::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getObject(%L, %T::class.java)", rsName, i, BigDecimal::class) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setObject(%L, %L, %T.NUMERIC)", stmt, idx, variableName, Types::class) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.NUMERIC)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            BigDecimal::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getObject(%L, %T::class.java)", rsName, i, BigDecimal::class) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setObject(%L, %L, %T.NUMERIC)", stmt, idx, variableName, Types::class) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.NUMERIC)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            ByteArray::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getBytes(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setBytes(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.NUMERIC)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            ByteArray::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getBytes(%L)", rsName, i) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setBytes(%L, %L)", stmt, idx, variableName) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.NUMERIC)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            LocalDateTime::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getObject(%L, %T::class.java)", rsName, i, LocalDateTime::class.java) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setObject(%L, %L, %T.TIMESTAMP)", stmt, idx, variableName, Types::class) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.TIMESTAMP)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            LocalDateTime::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getObject(%L, %T::class.java)", rsName, i, LocalDateTime::class.java) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setObject(%L, %L, %T.TIMESTAMP)", stmt, idx, variableName, Types::class) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.TIMESTAMP)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            LocalDate::class.asTypeName(),
            { rsName, i -> CodeBlock.of("%N.getObject(%L, %T::class.java)", rsName, i, LocalDate::class.java) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setObject(%L, %L, %T.DATE)", stmt, idx, variableName, Types::class) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.NUMERIC)", stmt, idx, Types::class) }
        ),
        JdbcNativeType.of(
            LocalDate::class.asTypeName().copy(true),
            { rsName, i -> CodeBlock.of("%N.getObject(%L, %T::class.java)", rsName, i, LocalDate::class.java) },
            { stmt, variableName, idx -> CodeBlock.of("%N.setObject(%L, %L, %T.DATE)", stmt, idx, variableName, Types::class) },
            { stmt, idx -> CodeBlock.of("%N.setNull(%L, %T.NUMERIC)", stmt, idx, Types::class) }
        ),
    )

    fun findNativeType(typeName: TypeName) = types.firstOrNull { it.type() == typeName }
}
