package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>Русский</b>: Контракт для создания конвертера <b>результата</b> CQL запроса.
 * <br>
 * Предоставляется над возвращаемым значением метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a CQL query <b>result</b> converter.
 * <br>
 * Provided over the return value of the method via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends CassandraRepository {
 *
 *     @Mapping(MyCassandraResultSetMapper.class)
 *     @Query("SELECT u.name, u.surname FROM users u")
 *     List<User> findAll();
 * }
 * }
 * </pre>
 *
 * @see CassandraRepository
 * @see Mapping
 */
public interface CassandraResultSetMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(ResultSet rows);

    static <T> CassandraResultSetMapper<T> singleResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return rs -> {
            for (var row : rs) {
                return rowMapper.apply(row);
            }
            return null;
        };
    }

    static <T> CassandraResultSetMapper<Optional<T>> optionalResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return rs -> {
            for (var row : rs) {
                return Optional.ofNullable(rowMapper.apply(row));
            }
            return Optional.empty();
        };
    }

    static <T> CassandraResultSetMapper<List<T>> listResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return rs -> {
            var list = new ArrayList<T>(rs.getAvailableWithoutFetching());
            for (var row : rs) {
                list.add(rowMapper.apply(row));
            }
            return list;
        };
    }
}
