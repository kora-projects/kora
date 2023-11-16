package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import ru.tinkoff.kora.common.Mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface CassandraAsyncResultSetMapper<T> extends Mapping.MappingFunction {

    CompletionStage<T> apply(AsyncResultSet rows);

    static <T> CassandraAsyncResultSetMapper<T> one(CassandraRowMapper<T> rowMapper) {
        return rows -> {
            var first = rows.one();
            if (first != null) {
                return CompletableFuture.completedFuture(rowMapper.apply(first));
            } else {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    static <T> CassandraAsyncResultSetMapper<List<T>> list(CassandraRowMapper<T> rowMapper) {
        return rs -> {
            var result = new ArrayList<T>(rs.remaining());
            return extractAndMapRows(rs, result, rowMapper);
        };
    }

    private static <T> CompletionStage<List<T>> extractAndMapRows(AsyncResultSet resultSet, List<T> previousResults, CassandraRowMapper<T> rowMapper) {
        for (var row : resultSet.currentPage()) {
            previousResults.add(rowMapper.apply(row));
        }
        if (resultSet.hasMorePages()) {
            return resultSet.fetchNextPage().thenCompose(nextRs -> extractAndMapRows(nextRs, previousResults, rowMapper));
        } else {
            return CompletableFuture.completedFuture(previousResults);
        }
    }
}
