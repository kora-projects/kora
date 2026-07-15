package io.koraframework.database.jdbc;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public interface CollectionJdbcResultSetMappersModule {

    @DefaultComponent
    default <T> JdbcResultSetMapper<Optional<T>> optionalResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return JdbcResultSetMapper.optionalResultSetMapper(rowMapper);
    }

    @DefaultComponent
    default <T> JdbcResultSetMapper<Set<T>> setResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return rs -> {
            var set = new HashSet<T>();
            while (rs.next()) {
                set.add(rowMapper.apply(rs));
            }
            return set;
        };
    }

    @DefaultComponent
    default <T> JdbcResultSetMapper<Collection<T>> collectionResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return rs -> {
            var list = new ArrayList<T>();
            while (rs.next()) {
                list.add(rowMapper.apply(rs));
            }
            return list;
        };
    }
}
