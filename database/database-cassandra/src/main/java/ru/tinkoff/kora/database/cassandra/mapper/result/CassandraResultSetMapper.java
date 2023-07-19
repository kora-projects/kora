package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import ru.tinkoff.kora.common.Mapping;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
