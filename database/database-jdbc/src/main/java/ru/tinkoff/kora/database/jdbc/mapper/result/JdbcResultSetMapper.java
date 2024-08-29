package ru.tinkoff.kora.database.jdbc.mapper.result;

import ru.tinkoff.kora.common.Mapping;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>Русский</b>: Контракт для создания конвертера <b>результата</b> SQL запроса.
 * <br>
 * Предоставляется над возвращаемым значением метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a SQL query <b>result</b> converter.
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
 *     @Mapping(MyJdbcResultSetMapper.class)
 *     @Query("SELECT u.name, u.surname FROM users u")
 *     List<User> findAll();
 * }
 * }
 * </pre>
 *
 * @see JdbcRepository
 * @see Mapping
 */
public interface JdbcResultSetMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(ResultSet rows) throws SQLException;

    static <T> JdbcResultSetMapper<T> singleResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return rs -> {
            if (!rs.next()) {
                return null;
            }
            return rowMapper.apply(rs);
        };
    }

    static <T> JdbcResultSetMapper<List<T>> listResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return rs -> {
            var list = new ArrayList<T>();
            while (rs.next()) {
                var row = rowMapper.apply(rs);
                list.add(row);
            }
            return list;
        };
    }

    static <T> JdbcResultSetMapper<Optional<T>> optionalResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return rs -> {
            if (rs.next()) {
                return Optional.ofNullable(rowMapper.apply(rs));
            }
            return Optional.empty();
        };
    }
}
