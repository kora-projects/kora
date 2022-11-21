package ru.tinkoff.kora.application.graph;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class TypeRef<T> implements ParameterizedType {
    private final Class<T> rawType;
    private final Type[] actualTypeArguments;

    private TypeRef(Class<T> rawType, Type... actualTypeArguments) {
        this.rawType = rawType;
        this.actualTypeArguments = actualTypeArguments;
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeRef<T> of(Class<? super T> rawType, Type... actualTypeArguments) {
        var args = new Type[actualTypeArguments.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = actualTypeArguments[i] instanceof TypeRef<?> typeRef && typeRef.actualTypeArguments.length == 0
                ? typeRef.rawType
                : actualTypeArguments[i];
        }
        return new TypeRef<>((Class<T>) rawType, args);
    }

    @Override
    public Type[] getActualTypeArguments() {
        return this.actualTypeArguments;
    }

    @Override
    public Class<T> getRawType() {
        return this.rawType;
    }

    @Override
    public Type getOwnerType() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof ParameterizedType that) {

            var thatOwner = that.getOwnerType();
            var thatRawType = that.getRawType();

            return
                Objects.equals(getOwnerType(), thatOwner) &&
                Objects.equals(getRawType(), thatRawType) &&
                Arrays.equals(getActualTypeArguments(), that.getActualTypeArguments());
        } else if (o instanceof Class<?> type && this.actualTypeArguments.length == 0) {
            return this.rawType.equals(type);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(rawType);
        result = 31 * result + Arrays.hashCode(actualTypeArguments);
        return result;
    }

    @Override
    public String toString() {
        var rawTypeString = rawType.getCanonicalName();
//            .replace("class ", "")
//            .replace("interface ", "");
        if (actualTypeArguments.length == 0) {
            return "" + rawTypeString;
        }
        var typeParams = Arrays.stream(actualTypeArguments)
            .map(t -> t instanceof Class<?> c
                    ? c.getCanonicalName()
                    : t.getTypeName()
//                .replace("class ", "")
//                .replace("interface ", "")
            )
            .collect(Collectors.joining(", ", "<", ">"));
        return "" + rawTypeString + typeParams;
    }

//    private static String typeToString(Type type) {
//
//    }
}
