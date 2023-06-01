package ru.tinkoff.kora.json.annotation.processor;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import javax.annotation.Nullable;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

public class KnownType {
    private final Map<TypeName, KnownTypesEnum> knownTypes;

    public KnownType() {
        this.knownTypes = Map.ofEntries(
            Map.entry(ClassName.get(UUID.class), KnownTypesEnum.UUID),
            Map.entry(ClassName.get(String.class), KnownTypesEnum.STRING),
            Map.entry(TypeName.BOOLEAN, KnownTypesEnum.BOOLEAN_PRIMITIVE),
            Map.entry(TypeName.BOOLEAN.box(), KnownTypesEnum.BOOLEAN_OBJECT),
            Map.entry(TypeName.SHORT, KnownTypesEnum.SHORT_PRIMITIVE),
            Map.entry(TypeName.SHORT.box(), KnownTypesEnum.SHORT_OBJECT),
            Map.entry(TypeName.INT, KnownTypesEnum.INTEGER_PRIMITIVE),
            Map.entry(TypeName.INT.box(), KnownTypesEnum.INTEGER_OBJECT),
            Map.entry(TypeName.LONG, KnownTypesEnum.LONG_PRIMITIVE),
            Map.entry(TypeName.LONG.box(), KnownTypesEnum.LONG_OBJECT),
            Map.entry(TypeName.DOUBLE, KnownTypesEnum.DOUBLE_PRIMITIVE),
            Map.entry(TypeName.DOUBLE.box(), KnownTypesEnum.DOUBLE_OBJECT),
            Map.entry(TypeName.FLOAT, KnownTypesEnum.FLOAT_PRIMITIVE),
            Map.entry(TypeName.FLOAT.box(), KnownTypesEnum.FLOAT_OBJECT),
            Map.entry(ClassName.get(BigInteger.class), KnownTypesEnum.BIG_INTEGER),
            Map.entry(ClassName.get(BigDecimal.class), KnownTypesEnum.BIG_DECIMAL),
            Map.entry(ArrayTypeName.of(TypeName.BYTE), KnownTypesEnum.BINARY)
        );
    }


    @Nullable
    public KnownTypesEnum detect(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.ERROR) {
            return null;
        }
        return knownTypes.get(TypeName.get(typeMirror));

    }

    public enum KnownTypesEnum {
        STRING,
        BOOLEAN_OBJECT,
        BOOLEAN_PRIMITIVE,
        INTEGER_OBJECT,
        INTEGER_PRIMITIVE,
        BIG_INTEGER,
        BIG_DECIMAL,
        DOUBLE_OBJECT,
        DOUBLE_PRIMITIVE,
        FLOAT_OBJECT,
        FLOAT_PRIMITIVE,
        LONG_OBJECT,
        LONG_PRIMITIVE,
        SHORT_OBJECT,
        SHORT_PRIMITIVE,
        BINARY,
        UUID,
    }
}
