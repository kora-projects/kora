package ru.tinkoff.kora.database.r2dbc.mapper.parameter;

import io.r2dbc.spi.Statement;
import ru.tinkoff.kora.common.Mapping;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.r2dbc.R2dbcRepository;

/**
 * <b>Русский</b>: Контракт для создания конвертер входящих параметров SQL запроса.
 * Предоставляется над аргументом метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a converter of incoming SQL query parameters.
 * Provided over the method argument via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends R2dbcRepository {
 *
 *     @Query("INSERT INTO users(fullname) VALUES (:fullName)")
 *     void addUser(@Mapping(MyR2dbcParameterColumnMapper.class) String fullName);
 * }
 * }
 * </pre>
 *
 * @see R2dbcRepository
 * @see Mapping
 */
public interface R2dbcParameterColumnMapper<T> extends Mapping.MappingFunction {
    void apply(Statement stmt, int index, @Nullable T value);
}
