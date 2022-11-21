package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class AsyncResultSetExtractor {
    public static <T> CompletionStage<List<T>> extractAndMapRows(AsyncResultSet resultSet, List<T> previousResults, CassandraRowMapper<T> rowMapper) {
        for (var row : resultSet.currentPage()) {
            try {
                previousResults.add(rowMapper.apply(row));
            } catch (Throwable throwable) {
                throw Exceptions.propagate(throwable);
            }
        }
        if (resultSet.hasMorePages()) {
            return resultSet.fetchNextPage().thenCompose(nextRs -> extractAndMapRows(nextRs, previousResults, rowMapper));
        } else {
            return CompletableFuture.completedFuture(previousResults);
        }
    }

    public static <T> Mono<List<T>> extractAndMapRowsMono(AsyncResultSet resultSet, List<T> previousResults, CassandraRowMapper<T> rowMapper) {
        return Mono.create(sink -> extract(resultSet, rowMapper, sink, previousResults));
    }

    private static <T> void extract(AsyncResultSet rs, CassandraRowMapper<T> rowMapper, MonoSink<List<T>> sink, List<T> state) {
        for (var row : rs.currentPage()) {
            try {
                state.add(rowMapper.apply(row));
            } catch (Exception e) {
                sink.error(e);
                return;
            }
        }
        if (rs.hasMorePages()) {
            rs.fetchNextPage().handle((_rs, exception) -> {
                if (_rs != null) {
                    extract(_rs, rowMapper, sink, state);
                } else {
                    sink.error(exception);
                }
                return null;
            });
        } else {
            sink.success(state);
        }
    }
}
