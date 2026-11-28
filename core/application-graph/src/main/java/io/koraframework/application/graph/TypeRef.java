package io.koraframework.application.graph;

import java.lang.reflect.*;
import java.util.*;

public class TypeRef<T> implements ParameterizedType {
    private final Class<T> rawType;
    private final Type[] actualTypeArguments;
    private final int hashCode;

    private TypeRef(Class<T> rawType, Type... actualTypeArguments) {
        this.rawType = rawType;
        this.actualTypeArguments = actualTypeArguments;
        int result = rawType.hashCode();
        result = 31 * result + Arrays.hashCode(actualTypeArguments);
        hashCode = result;
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeRef<T> of(Class<? super T> rawType, Type... actualTypeArguments) {
        var args = new Type[actualTypeArguments.length];
        for (int i = 0; i < args.length; i++) {
            var arg = actualTypeArguments[i];
            if (arg instanceof TypeRef) {
                var typeRef = (TypeRef<?>) arg;
                if (typeRef.actualTypeArguments.length == 0) {
                    args[i] = typeRef.rawType;
                    continue;
                }
            }
            args[i] = arg;
        }
        return new TypeRef<>((Class<T>) rawType, args);
    }

    @Override
    public Type[] getActualTypeArguments() {
        return actualTypeArguments;
    }

    @Override
    public Class<T> getRawType() {
        return rawType;
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
        if (o instanceof ParameterizedType) {
            var that = (ParameterizedType) o;
            if (rawType != that.getRawType()) {
                return false;
            }
            if (that.getOwnerType() != null) {
                return false;
            }
            return Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
        }
        if (o instanceof Class) {
            var type = (Class<?>) o;
            if (actualTypeArguments.length == 0) {
                return rawType.equals(type);
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        var rawTypeString = rawType.getName();
        if (actualTypeArguments.length == 0) {
            return rawTypeString;
        }
        var sb = new StringBuilder(rawTypeString).append("<");
        for (int i = 0; i < actualTypeArguments.length; i++) {
            var t = actualTypeArguments[i];
            if (t instanceof Class) {
                var c = (Class<?>) t;
                sb.append(c.getName());
            } else {
                sb.append(t.getTypeName());
            }
            if (i < actualTypeArguments.length - 1) {
                sb.append(", ");
            }
        }
        return sb.append(">").toString();
    }
}
