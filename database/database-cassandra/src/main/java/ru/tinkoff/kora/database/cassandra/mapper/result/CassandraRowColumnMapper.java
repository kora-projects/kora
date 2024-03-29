package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.oss.driver.api.core.data.GettableByName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.RowMapper;

public interface CassandraRowColumnMapper<T> extends Mapping.MappingFunction, RowMapper<T> {
    @Nullable
    T apply(GettableByName row, int index);
}
