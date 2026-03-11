package io.koraframework.database.jdbc.mapper.result;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.Mapping;
import io.koraframework.database.common.RowMapper;
import io.koraframework.database.jdbc.JdbcRepository;

import java.sql.ResultSet;
import java.sql.SQLException;

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
 * public interface MyRepository extends JdbcRepository {
 *
 *     @Mapping(MyJdbcRowMapper.class)
 *     @Query("SELECT u.name, u.surname FROM users u")
 *     List<User> findAll();
 * }
 * }
 * </pre>
 *
 * @see JdbcRepository
 * @see Mapping
 */
public interface JdbcRowMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    @Nullable
    T apply(ResultSet row) throws SQLException;
}
