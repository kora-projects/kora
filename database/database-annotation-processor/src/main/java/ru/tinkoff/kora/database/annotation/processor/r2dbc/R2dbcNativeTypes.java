package ru.tinkoff.kora.database.annotation.processor.r2dbc;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public class R2dbcNativeTypes {

    private static final Set<TypeName> nativeTypes = Set.of(
        TypeName.INT,
        TypeName.INT.box(),
        TypeName.LONG,
        TypeName.LONG.box(),
        TypeName.DOUBLE,
        TypeName.DOUBLE.box(),
        TypeName.BOOLEAN,
        TypeName.BOOLEAN.box(),
        ClassName.get(String.class),
        ClassName.get(BigDecimal.class),
        ClassName.get(BigInteger.class),
        ClassName.get(LocalDateTime.class),
        ClassName.get(LocalDate.class),
        ArrayTypeName.of(TypeName.BYTE)
    );

    @Nullable
    public static TypeName findAndBox(TypeName typeName) {
        if (nativeTypes.contains(typeName)) {
            return typeName.isPrimitive()
                ? typeName.box()
                : typeName;
        }
        return null;
    }
}
