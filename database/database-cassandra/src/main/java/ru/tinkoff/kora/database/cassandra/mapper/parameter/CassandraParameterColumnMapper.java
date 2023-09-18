package ru.tinkoff.kora.database.cassandra.mapper.parameter;

import com.datastax.oss.driver.api.core.data.SettableByName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;

public interface CassandraParameterColumnMapper<T> extends Mapping.MappingFunction {
    void apply(SettableByName<?> stmt, int index, @Nullable T value);
}
