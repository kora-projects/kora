package ru.tinkoff.kora.json.annotation.processor;

import javax.annotation.Nullable;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

public class KnownType {
    private final Elements elements;
    private final Types types;
    private final TypeMirror object;
    private final TypeMirror string;
    private final TypeMirror booleanObject;
    private final TypeMirror booleanPrimitive;
    private final TypeMirror integerObject;
    private final TypeMirror integerPrimitive;
    private final TypeMirror bigInteger;
    private final TypeMirror bigDecimal;
    private final TypeMirror doubleObject;
    private final TypeMirror doublePrimitive;
    private final TypeMirror floatObject;
    private final TypeMirror floatPrimitive;
    private final TypeMirror longObject;
    private final TypeMirror longPrimitive;
    private final TypeMirror shortObject;
    private final TypeMirror shortPrimitive;
    private final TypeMirror binary;
    private final TypeMirror localDate;
    private final TypeMirror listErasure;
    private final TypeMirror setErasure;
    private final TypeMirror mapErasure;
    private final TypeMirror sortedSetErasure;
    private final TypeMirror localDateTime;
    private final TypeMirror offsetDateTime;
    private final TypeMirror uuid;

    public KnownType(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;

        this.object = this.elements.getTypeElement("java.lang.Object").asType();
        this.string = this.elements.getTypeElement("java.lang.String").asType();
        this.booleanObject = this.elements.getTypeElement("java.lang.Boolean").asType();
        this.booleanPrimitive = this.types.unboxedType(this.booleanObject);
        this.integerObject = this.elements.getTypeElement("java.lang.Integer").asType();
        this.integerPrimitive = this.types.unboxedType(this.integerObject);
        this.bigInteger = this.elements.getTypeElement("java.math.BigInteger").asType();
        this.bigDecimal = this.elements.getTypeElement("java.math.BigDecimal").asType();
        this.doubleObject = this.elements.getTypeElement("java.lang.Double").asType();
        this.doublePrimitive = this.types.unboxedType(this.doubleObject);
        this.floatObject = this.elements.getTypeElement("java.lang.Float").asType();
        this.floatPrimitive = this.types.unboxedType(this.floatObject);
        this.longObject = this.elements.getTypeElement("java.lang.Long").asType();
        this.longPrimitive = this.types.unboxedType(this.longObject);
        this.shortObject = this.elements.getTypeElement("java.lang.Short").asType();
        this.shortPrimitive = this.types.unboxedType(this.shortObject);
        this.localDate = this.elements.getTypeElement("java.time.LocalDate").asType();
        this.localDateTime = this.elements.getTypeElement("java.time.LocalDateTime").asType();
        this.offsetDateTime = this.elements.getTypeElement("java.time.OffsetDateTime").asType();
        this.uuid = this.elements.getTypeElement("java.util.UUID").asType();
        this.binary = this.types.getArrayType(this.types.getPrimitiveType(TypeKind.BYTE));

        this.listErasure = this.types.erasure(this.elements.getTypeElement(List.class.getCanonicalName()).asType());
        this.setErasure = this.types.erasure(this.elements.getTypeElement(Set.class.getCanonicalName()).asType());
        this.mapErasure = this.types.erasure(this.elements.getTypeElement(Map.class.getCanonicalName()).asType());
        this.sortedSetErasure = this.types.erasure(this.elements.getTypeElement(SortedSet.class.getCanonicalName()).asType());
    }


    @Nullable
    public KnownTypesEnum detect(TypeMirror typeMirror) {
        if (this.types.isSameType(typeMirror, string)) {
            return KnownTypesEnum.STRING;
        }
        if (this.types.isSameType(typeMirror, integerObject)) {
            return KnownTypesEnum.INTEGER_OBJECT;
        }
        if (this.types.isSameType(typeMirror, integerPrimitive)) {
            return KnownTypesEnum.INTEGER_PRIMITIVE;
        }
        if (this.types.isSameType(typeMirror, longObject)) {
            return KnownTypesEnum.LONG_OBJECT;
        }
        if (this.types.isSameType(typeMirror, longPrimitive)) {
            return KnownTypesEnum.LONG_PRIMITIVE;
        }
        if (this.types.isSameType(typeMirror, booleanObject)) {
            return KnownTypesEnum.BOOLEAN_OBJECT;
        }
        if (this.types.isSameType(typeMirror, booleanPrimitive)) {
            return KnownTypesEnum.BOOLEAN_PRIMITIVE;
        }
        if (this.types.isSameType(typeMirror, bigInteger)) {
            return KnownTypesEnum.BIG_INTEGER;
        }
        if (this.types.isSameType(typeMirror, bigDecimal)) {
            return KnownTypesEnum.BIG_DECIMAL;
        }
        if (this.types.isSameType(typeMirror, doubleObject)) {
            return KnownTypesEnum.DOUBLE_OBJECT;
        }
        if (this.types.isSameType(typeMirror, doublePrimitive)) {
            return KnownTypesEnum.DOUBLE_PRIMITIVE;
        }
        if (this.types.isSameType(typeMirror, floatObject)) {
            return KnownTypesEnum.FLOAT_OBJECT;
        }
        if (this.types.isSameType(typeMirror, floatPrimitive)) {
            return KnownTypesEnum.FLOAT_PRIMITIVE;
        }
        if (this.types.isSameType(typeMirror, shortObject)) {
            return KnownTypesEnum.SHORT_OBJECT;
        }
        if (this.types.isSameType(typeMirror, shortPrimitive)) {
            return KnownTypesEnum.SHORT_PRIMITIVE;
        }
        if (this.types.isSameType(typeMirror, localDate)) {
            return KnownTypesEnum.LOCAL_DATE;
        }
        if (this.types.isSameType(typeMirror, localDateTime)) {
            return KnownTypesEnum.LOCAL_DATE_TIME;
        }
        if (this.types.isSameType(typeMirror, offsetDateTime)) {
            return KnownTypesEnum.OFFSET_DATE_TIME;
        }
        if (this.types.isSameType(typeMirror, binary)) {
            return KnownTypesEnum.BINARY;
        }
        if (this.types.isSameType(typeMirror, uuid)) {
            return KnownTypesEnum.UUID;
        }
        return null;
    }

    @Nullable
    public TypeMirror extractList(TypeMirror typeMirror) {
        if (this.types.isSameType(this.types.erasure(typeMirror), this.listErasure)) {
            var declaredType = (DeclaredType) typeMirror;
            return declaredType.getTypeArguments().get(0);
        }
        return null;
    }

    @Nullable
    public TypeMirror extractSet(TypeMirror typeMirror) {
        if (this.types.isSameType(this.types.erasure(typeMirror), this.setErasure)) {
            var declaredType = (DeclaredType) typeMirror;
            return declaredType.getTypeArguments().get(0);
        }
        return null;
    }

    @Nullable
    public TypeMirror extractMap(TypeMirror typeMirror) {
        if (this.types.isSameType(this.types.erasure(typeMirror), this.mapErasure)) {
            var declaredType = (DeclaredType) typeMirror;
            if (this.types.isSameType(this.string, declaredType.getTypeArguments().get(0))) {
                return declaredType.getTypeArguments().get(1);
            }
        }
        return null;
    }

    @Nullable
    public TypeMirror extractSortedSet(TypeMirror typeMirror) {
        if (this.types.isSameType(this.types.erasure(typeMirror), this.sortedSetErasure)) {
            var declaredType = (DeclaredType) typeMirror;
            return declaredType.getTypeArguments().get(0);
        }
        return null;
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
        LOCAL_DATE,
        LOCAL_DATE_TIME,
        OFFSET_DATE_TIME,
        UUID,
    }
}
