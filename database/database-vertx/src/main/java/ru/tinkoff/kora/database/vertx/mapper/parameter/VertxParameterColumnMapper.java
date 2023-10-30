package ru.tinkoff.kora.database.vertx.mapper.parameter;

import ru.tinkoff.kora.common.Mapping;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.vertx.VertxRepository;


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
 * public interface MyRepository extends VertxRepository {
 *
 *     @Query("INSERT INTO users(fullname) VALUES (:fullName)")
 *     void addUser(@Mapping(MyVertxParameterColumnMapper.class) String fullName);
 * }
 * }
 * </pre>
 *
 * @see VertxRepository
 * @see Mapping
 */
public interface VertxParameterColumnMapper<T> extends Mapping.MappingFunction {
    @Nullable
    Object apply(@Nullable T value);
}
