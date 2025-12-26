package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.oss.driver.api.core.cql.Row;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;
import ru.tinkoff.kora.database.common.RowMapper;

/**
 * <b>Русский</b>: Контракт для создания конвертера <b>строки</b> CQL запроса.
 * <br>
 * Предоставляется над возвращаемым значением метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a CQL query <b>row</b> converter.
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
 *     @Mapping(MyCassandraRowMapper.class)
 *     @Query("SELECT u.name, u.surname FROM users u")
 *     List<User> findAll();
 * }
 * }
 * </pre>
 *
 * @see CassandraRepository
 * @see Mapping
 */
public interface CassandraRowMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    @Nullable
    T apply(Row row);
}
