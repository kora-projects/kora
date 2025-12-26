package ru.tinkoff.kora.database.cassandra.mapper.parameter;

import com.datastax.oss.driver.api.core.data.SettableByName;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;

/**
 * <b>Русский</b>: Контракт для создания конвертер входящих параметров CQL запроса.
 * Предоставляется над аргументом метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a converter of incoming CQL query parameters.
 * Provided over the method argument via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends CassandraRepository {
 *
 *     @Query("INSERT INTO users(fullname) VALUES (:fullName)")
 *     void addUser(@Mapping(MyCassandraParameterColumnMapper.class) String fullName);
 * }
 * }
 * </pre>
 *
 * @see CassandraRepository
 * @see Mapping
 */
public interface CassandraParameterColumnMapper<T> extends Mapping.MappingFunction {
    void apply(SettableByName<?> stmt, int index, @Nullable T value);
}
