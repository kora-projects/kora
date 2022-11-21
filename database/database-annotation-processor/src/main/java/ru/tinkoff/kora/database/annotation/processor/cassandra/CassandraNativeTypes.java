package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class CassandraNativeTypes {
    private static final List<CassandraNativeType> nativeTypes;

    static {
        var booleanPrimitive = CassandraNativeType.of(
            TypeName.BOOLEAN,
            (rsName, i) -> CodeBlock.of("$L.getBoolean($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setBoolean($L, $L)", stmt, i, var)
        );
        var booleanBoxed = booleanPrimitive.boxed();
        var intPrimitive = CassandraNativeType.of(
            TypeName.INT,
            (rsName, i) -> CodeBlock.of("$L.getInt($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setInt($L, $L)", stmt, i, var)
        );
        var intBoxed = intPrimitive.boxed();
        var longPrimitive = CassandraNativeType.of(
            TypeName.LONG,
            (rsName, i) -> CodeBlock.of("$L.getLong($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setLong($L, $L)", stmt, i, var)
        );
        var longBoxed = longPrimitive.boxed();
        var doublePrimitive = CassandraNativeType.of(
            TypeName.DOUBLE,
            (rsName, i) -> CodeBlock.of("$L.getDouble($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setDouble($L, $L)", stmt, i, var)
        );
        var doubleBoxed = doublePrimitive.boxed();
        var string = CassandraNativeType.of(
            ClassName.get(String.class),
            (rsName, i) -> CodeBlock.of("$L.getString($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setString($L, $L)", stmt, i, var)
        );
        var bigDecimal = CassandraNativeType.of(
            ClassName.get(BigDecimal.class),
            (rsName, i) -> CodeBlock.of("$L.getBigDecimal($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setBigDecimal($L, $L)", stmt, i, var)
        );
        var byteBuffer = CassandraNativeType.of(
            ClassName.get(ByteBuffer.class),
            (rsName, i) -> CodeBlock.of("$L.getByteBuffer($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setByteBuffer($L, $L)", stmt, i, var)
        );
        var localDateTime = CassandraNativeType.of(
            TypeName.get(LocalDateTime.class),
            (rsName, i) -> CodeBlock.of("$L.get($L, $T.class)", rsName, i, LocalDateTime.class),
            (stmt, var, i) -> CodeBlock.of("$L.set($L, $L, $T.class)", stmt, i, var, LocalDateTime.class)
        );
        var localDate = CassandraNativeType.of(
            TypeName.get(LocalDate.class),
            (rsName, i) -> CodeBlock.of("$L.getLocalDate($L)", rsName, i),
            (stmt, var, i) -> CodeBlock.of("$L.setLocalDate($L, $L)", stmt, i, var)
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
            byteBuffer,
            localDateTime,
            localDate
        );
    }

    @Nullable
    public static CassandraNativeType findNativeType(TypeName typeName) {
        for (var nativeParameterType : nativeTypes) {
            if (Objects.equals(nativeParameterType.type(), typeName)) {
                return nativeParameterType;
            }
        }
        return null;
    }
}
