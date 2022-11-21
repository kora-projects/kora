package ru.tinkoff.kora.database.jdbc.mapper.result;

import ru.tinkoff.kora.common.Mapping;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface JdbcResultSetMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(ResultSet rs) throws SQLException;

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
