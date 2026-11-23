package io.koraframework.application.graph;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
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
        return new TypeRef<>((Class<T>) rawType, unwrap(actualTypeArguments));
    }

    /**
     * <b>Русский</b>: Создает wildcard тип ({@code ?}, {@code ? extends T} или {@code ? super T}) для использования как аргумент типа.
     * <hr>
     * <b>English</b>: Creates a wildcard type ({@code ?}, {@code ? extends T} or {@code ? super T}) to be used as a type argument.
     */
    public static WildcardType wildcard(Type[] upperBounds, Type[] lowerBounds) {
        return new WildcardTypeImpl(upperBounds.length == 0 ? new Type[]{Object.class} : unwrap(upperBounds), unwrap(lowerBounds));
    }

    /**
     * <b>Русский</b>: Создает тип массива с параметризованным типом элемента (например {@code List<String>[]}) для использования как аргумент типа.
     * <hr>
     * <b>English</b>: Creates an array type with a parameterized component type (e.g. {@code List<String>[]}) to be used as a type argument.
     */
    public static GenericArrayType arrayOf(Type componentType) {
        return new GenericArrayTypeImpl(unwrap(new Type[]{componentType})[0]);
    }

    private static Type[] unwrap(Type[] types) {
        var result = new Type[types.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = types[i] instanceof TypeRef<?> typeRef && typeRef.actualTypeArguments.length == 0
                ? typeRef.rawType
                : types[i];
        }
        return result;
    }

    // equals and hashCode follow the JDK implementation, so these types are equal to the ones obtained via reflection
    private record WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) implements WildcardType {
        @Override
        public Type[] getUpperBounds() {
            return this.upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return this.lowerBounds.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof WildcardType that
                && Arrays.equals(this.lowerBounds, that.getLowerBounds())
                && Arrays.equals(this.upperBounds, that.getUpperBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.lowerBounds) ^ Arrays.hashCode(this.upperBounds);
        }

        @Override
        public String toString() {
            if (this.lowerBounds.length > 0) {
                return "? super " + this.lowerBounds[0].getTypeName();
            }
            if (this.upperBounds[0] == Object.class) {
                return "?";
            }
            return "? extends " + this.upperBounds[0].getTypeName();
        }
    }

    private record GenericArrayTypeImpl(Type genericComponentType) implements GenericArrayType {
        @Override
        public Type getGenericComponentType() {
            return this.genericComponentType;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GenericArrayType that && Objects.equals(this.genericComponentType, that.getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.genericComponentType);
        }

        @Override
        public String toString() {
            return this.genericComponentType.getTypeName() + "[]";
        }
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
