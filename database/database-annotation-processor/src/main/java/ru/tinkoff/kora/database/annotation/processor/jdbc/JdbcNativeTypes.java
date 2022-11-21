package ru.tinkoff.kora.database.annotation.processor.jdbc;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class JdbcNativeTypes {
    private static final List<JdbcNativeType> nativeTypes;

    static {
        var booleanPrimitive = JdbcNativeType.of(
            TypeName.BOOLEAN,
            (rsName, i) -> CodeBlock.of("$L.getBoolean($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setBoolean($L, $L)", stmt, i, var),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.BOOLEAN)", stmtName, i, java.sql.Types.class)
        );
        var booleanBoxed = booleanPrimitive.boxed();
        var intPrimitive = JdbcNativeType.of(
            TypeName.INT,
            (rsName, i) -> CodeBlock.of("$L.getInt($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setInt($L, $L)", stmt, i, var),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.INTEGER)", stmtName, i, java.sql.Types.class)
        );
        var intBoxed = intPrimitive.boxed();
        var longPrimitive = JdbcNativeType.of(
            TypeName.LONG,
            (rsName, i) -> CodeBlock.of("$L.getLong($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setLong($L, $L)", stmt, i, var),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.BIGINT)", stmtName, i, java.sql.Types.class)
        );
        var longBoxed = longPrimitive.boxed();
        var doublePrimitive = JdbcNativeType.of(
            TypeName.DOUBLE,
            (rsName, i) -> CodeBlock.of("$L.getDouble($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setDouble($L, $L)", stmt, i, var),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.DOUBLE)", stmtName, i, java.sql.Types.class)
        );
        var doubleBoxed = doublePrimitive.boxed();
        var string = JdbcNativeType.of(
            ClassName.get(String.class),
            (rsName, i) -> CodeBlock.of("$L.getString($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setString($L, $L)", stmt, i, var),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.VARCHAR)", stmtName, i, java.sql.Types.class)
        );
        var bigDecimal = JdbcNativeType.of(
            ClassName.get(BigDecimal.class),
            (rsName, i) -> CodeBlock.of("$L.getObject($L, $T.class)", rsName, i, BigDecimal.class),
            (stmt, var, i) -> CodeBlock.of("$L.setObject($L, $L, $T.NUMERIC)", stmt, i, var, java.sql.Types.class),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.NUMERIC)", stmtName, i, java.sql.Types.class)
        );
        var byteArray = JdbcNativeType.of(
            ArrayTypeName.of(TypeName.BYTE),
            (rsName, i) -> CodeBlock.of("$L.getBytes($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setBytes($L, $L)", stmt, i, var),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.BINARY)", stmtName, i, java.sql.Types.class)
        );
        var localDateTime = JdbcNativeType.of(
            TypeName.get(LocalDateTime.class),
            (rsName, i) -> CodeBlock.of("$L.getObject($L, $T.class)", rsName, i, LocalDateTime.class),
            (stmt, var, i) -> CodeBlock.of("$L.setObject($L, $L, $T.TIMESTAMP)", stmt, i, var, java.sql.Types.class),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.TIMESTAMP)", stmtName, i, java.sql.Types.class)
        );
        var localDate = JdbcNativeType.of(
            TypeName.get(LocalDate.class),
            (rsName, i) -> CodeBlock.of("$L.getObject($L, $T.class)", rsName, i, LocalDate.class),
            (stmt, var, i) -> CodeBlock.of("$L.setObject($L, $L, $T.DATE)", stmt, i, var, java.sql.Types.class),
            (stmtName, i) -> CodeBlock.of("$L.setNull($L, $T.DATE)", stmtName, i, java.sql.Types.class)
        );

        nativeTypes = List.of(
            booleanPrimitive,
            booleanBoxed,
            intPrimitive,
            intBoxed,
            longPrimitive,
            longBoxed,
            doublePrimitive,
            doubleBoxed,
            string,
            bigDecimal,
            byteArray,
            localDateTime,
            localDate
        );
    }

    @Nullable
    public static JdbcNativeType findNativeType(TypeName typeName) {
        for (var nativeParameterType : nativeTypes) {
            if (Objects.equals(nativeParameterType.type(), typeName)) {
                return nativeParameterType;
            }
        }
        return null;
    }
}
