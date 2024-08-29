package ru.tinkoff.kora.database.r2dbc.mapper.result;

import io.r2dbc.spi.Row;
import ru.tinkoff.kora.common.Mapping;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.r2dbc.R2dbcRepository;

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
 * public record User(@Mapping(MyR2dbcRowColumnMapper.class) String fullName) {}
 * }
 * </pre>
 *
 * @see R2dbcRepository
 * @see Mapping
 */
public interface R2dbcResultColumnMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(Row row, String label);
}
