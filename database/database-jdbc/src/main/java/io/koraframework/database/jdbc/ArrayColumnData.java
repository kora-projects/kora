package io.koraframework.database.jdbc;

import java.util.function.Function;

public record ArrayColumnData<T>(String sqlTypeName,
                                 Function<T, Object> toDbElement,
                                 Function<Object, T> fromDbElement) {

    @SuppressWarnings("unchecked")
    public static <T> ArrayColumnData<T> withNoopMapping(String sqlTypeName) {
        return new ArrayColumnData<>(sqlTypeName, t -> t, o -> (T) o);
    }
}
