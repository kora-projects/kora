package ru.tinkoff.kora.database.vertx.mapper.result;

import io.vertx.sqlclient.Row;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.RowMapper;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.vertx.VertxRepository;

/**
 * <b>Русский</b>: Контракт для создания конвертера <b>строки</b> SQL запроса.
 * <br>
 * Предоставляется над возвращаемым значением метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a SQL query <b>row</b> converter.
 * <br>
 * Provided over the return value of the method via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends VertxRepository {
 *
 *     @Mapping(MyVertxRowMapper.class)
 *     @Query("SELECT u.name, u.surname FROM users u")
 *     List<User> findAll();
 * }
 * }
 * </pre>
 *
 * @see VertxRepository
 * @see Mapping
 */
public interface VertxRowMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    @Nullable
    T apply(Row row);
}
