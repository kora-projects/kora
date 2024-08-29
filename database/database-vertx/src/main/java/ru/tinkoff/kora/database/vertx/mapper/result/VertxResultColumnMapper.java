package ru.tinkoff.kora.database.vertx.mapper.result;

import io.vertx.sqlclient.Row;
import ru.tinkoff.kora.common.Mapping;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.vertx.VertxRepository;

/**
 * <b>Русский</b>: Контракт для создания конвертера <b>колонки</b> SQL запроса.
 * <br>
 * Предоставляется над полем сущности через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a SQL query <b>column</b> converter.
 * <br>
 * Provided over an entity field via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public record User(@Mapping(MyVertxRowColumnMapper.class) String fullName) {}
 * }
 * </pre>
 *
 * @see VertxRepository
 * @see Mapping
 */
public interface VertxResultColumnMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(Row row, int index);
}
