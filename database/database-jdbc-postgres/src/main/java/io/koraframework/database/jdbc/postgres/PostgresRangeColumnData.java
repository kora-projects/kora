package io.koraframework.database.jdbc.postgres;

import java.util.function.Function;

/**
 * Метаданные PostgreSQL range-типа: имя SQL-типа (например {@code int4range}) и конверсия одной границы
 * в/из строкового представления границы диапазона.
 */
public record PostgresRangeColumnData<T>(String rangeTypeName,
                                         Function<T, String> toBound,
                                         Function<String, T> fromBound) {
}
