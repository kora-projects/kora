package ru.tinkoff.kora.database.jdbc.mapper.result;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

import java.sql.ResultSet;
import java.sql.SQLException;

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
 * public record User(@Mapping(MyJdbcRowColumnMapper.class) String fullName) {}
 * }
 * </pre>
 *
 * @see JdbcRepository
 * @see Mapping
 */
public interface JdbcResultColumnMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(ResultSet row, int index) throws SQLException;
}
