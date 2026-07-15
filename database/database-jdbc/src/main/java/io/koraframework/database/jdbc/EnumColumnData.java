package io.koraframework.database.jdbc;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public record EnumColumnData<E extends Enum<E>, V>(Class<E> enumClass,
                                                   @Nullable String sqlTypeName,
                                                   Function<E, V> valueGetter,
                                                   Function<V, E> fromValueFactory) {

    public static <E extends Enum<E>, V> EnumColumnData<E, V> of(Class<E> enumClass, Function<E, V> valueGetter) {
        Map<V, E> reverse = new HashMap<>();
        for (var constant : enumClass.getEnumConstants()) {
            reverse.put(valueGetter.apply(constant), constant);
        }
        Function<V, E> fromValue = value -> {
            var constant = reverse.get(value);
            if (constant == null) {
                throw new IllegalArgumentException("Unknown db value '" + value + "' for enum " + enumClass.getName());
            }
            return constant;
        };
        return new EnumColumnData<>(enumClass, null, valueGetter, fromValue);
    }

    public static <E extends Enum<E>> EnumColumnData<E, String> byName(Class<E> enumClass) {
        return of(enumClass, Enum::name);
    }

    public static <E extends Enum<E>> EnumColumnData<E, Integer> byOrdinal(Class<E> enumClass) {
        return of(enumClass, Enum::ordinal);
    }

    public static <E extends Enum<E>> EnumColumnData<E, String> byStrategy(Class<E> enumClass, JdbcEnumValueMappingStrategy strategy) {
        return of(enumClass, constant -> strategy.map(constant.name()));
    }

    public EnumColumnData<E, V> withSqlTypeName(String sqlTypeName) {
        return new EnumColumnData<>(enumClass, sqlTypeName, valueGetter, fromValueFactory);
    }

    public EnumColumnData<E, V> withFromValueFactory(Function<V, E> fromValueFactory) {
        return new EnumColumnData<>(enumClass, sqlTypeName, valueGetter, fromValueFactory);
    }
}
