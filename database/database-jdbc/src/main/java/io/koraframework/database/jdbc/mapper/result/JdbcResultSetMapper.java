package io.koraframework.database.jdbc.mapper.result;

import io.koraframework.common.annotation.*;
import org.jspecify.annotations.*;

import java.sql.*;
import java.util.*;

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
        return new SingleResultSetMapper<>(rowMapper);
    }

    static <T> JdbcResultSetMapper<List<T>> listResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return new ListResultSetMapper<>(rowMapper);
    }

    static <T> JdbcResultSetMapper<Optional<T>> optionalResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return new OptionalResultSetMapper<>(rowMapper);
    }

    final class SingleResultSetMapper<T> implements JdbcResultSetMapper<T> {
        private final JdbcRowMapper<T> rowMapper;

        public SingleResultSetMapper(JdbcRowMapper<T> rowMapper) {
            this.rowMapper = rowMapper;
        }

        @Nullable
        @Override
        public T apply(ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return null;
            }
            return this.rowMapper.apply(rs);
        }
    }

    final class ListResultSetMapper<T> implements JdbcResultSetMapper<List<T>> {
        private final JdbcRowMapper<T> rowMapper;

        public ListResultSetMapper(JdbcRowMapper<T> rowMapper) {
            this.rowMapper = rowMapper;
        }

        @Override
        public List<T> apply(ResultSet rs) throws SQLException {
            var list = new ArrayList<T>();
            while (rs.next()) {
                list.add(this.rowMapper.apply(rs));
            }
            return list;
        }
    }

    final class OptionalResultSetMapper<T> implements JdbcResultSetMapper<Optional<T>> {
        private final JdbcRowMapper<T> rowMapper;

        public OptionalResultSetMapper(JdbcRowMapper<T> rowMapper) {
            this.rowMapper = rowMapper;
        }

        @Override
        public Optional<T> apply(ResultSet rs) throws SQLException {
            if (rs.next()) {
                return Optional.ofNullable(this.rowMapper.apply(rs));
            }
            return Optional.empty();
        }
    }
}
