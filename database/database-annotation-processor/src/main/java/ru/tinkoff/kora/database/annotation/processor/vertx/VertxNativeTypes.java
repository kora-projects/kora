package ru.tinkoff.kora.database.annotation.processor.vertx;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public class VertxNativeTypes {

    @Nullable
    public static VertxNativeType find(TypeName typeName) {
        return nativeTypes.get(typeName);
    }

    private static final Map<TypeName, VertxNativeType> nativeTypes = Map.ofEntries(
        Map.entry(TypeName.BOOLEAN, (row, column) -> CodeBlock.of("$L.getBoolean($L)", row, column)),
        Map.entry(TypeName.BOOLEAN.box(), (row, column) -> CodeBlock.of("$L.getBoolean($L)", row, column)),
        Map.entry(TypeName.INT, (row, column) -> CodeBlock.of("$L.getInteger($L)", row, column)),
        Map.entry(TypeName.INT.box(), (row, column) -> CodeBlock.of("$L.getInteger($L)", row, column)),
        Map.entry(TypeName.LONG, (row, column) -> CodeBlock.of("$L.getLong($L)", row, column)),
        Map.entry(TypeName.LONG.box(), (row, column) -> CodeBlock.of("$L.getLong($L)", row, column)),
        Map.entry(TypeName.DOUBLE, (row, column) -> CodeBlock.of("$L.getDouble($L)", row, column)),
        Map.entry(TypeName.DOUBLE.box(), (row, column) -> CodeBlock.of("$L.getDouble($L)", row, column)),
        Map.entry(ClassName.get(String.class), (row, column) -> CodeBlock.of("$L.getString($L)", row, column)),
        Map.entry(ClassName.get(BigDecimal.class), (row, column) -> CodeBlock.of("$L.getNumeric($L).bigDecimalValue()", row, column)),
        Map.entry(ClassName.get(BigInteger.class), (row, column) -> CodeBlock.of("$L.getBoolean($L).bigIntegerValue()", row, column)),
        Map.entry(ClassName.get(LocalDateTime.class), (row, column) -> CodeBlock.of("$L.getLocalDateTime($L)", row, column)),
        Map.entry(ClassName.get(LocalDate.class), (row, column) -> CodeBlock.of("$L.getLocalDate($L)", row, column)),
        Map.entry(ClassName.get("io.vertx.core.buffer", "Buffer"), (row, column) -> CodeBlock.of("$L.getBuffer($L)", row, column))
    );
}
