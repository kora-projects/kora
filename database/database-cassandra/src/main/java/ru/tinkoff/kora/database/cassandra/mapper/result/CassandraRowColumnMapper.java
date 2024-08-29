package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.oss.driver.api.core.data.GettableByName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;
import ru.tinkoff.kora.database.common.RowMapper;

/**
 * <b>Русский</b>: Контракт для создания конвертера <b>колонки</b> CQL запроса.
 * <br>
 * Предоставляется над полем сущности через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a CQL query <b>column</b> converter.
 * <br>
 * Provided over an entity field via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public record User(@Mapping(MyCassandraRowColumnMapper.class) String fullName) {}
 * }
 * </pre>
 *
 * @see CassandraRepository
 * @see Mapping
 */
public interface CassandraRowColumnMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    @Nullable
    T apply(GettableByName row, int index);
}
