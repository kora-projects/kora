package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;

import java.util.List;

public interface CassandraReactiveResultSetMapper<T, P extends Publisher<T>> extends Mapping.MappingFunction {
    P apply(ReactiveResultSet rows);

    static <T> CassandraReactiveResultSetMapper<T, Flux<T>> flux(CassandraRowMapper<T> rowMapper) {
        return rs -> Flux.from(rs).map(rowMapper::apply);
    }

    static <T> CassandraReactiveResultSetMapper<T, Mono<T>> mono(CassandraRowMapper<T> rowMapper) {
        return rs -> Flux.from(rs).next().handle((row, sink) -> {
            var mapped = rowMapper.apply(row);
            if (mapped != null) {
                sink.next(mapped);
            }
            sink.complete();
        });
    }

    static <T> CassandraReactiveResultSetMapper<List<T>, Mono<List<T>>> monoList(CassandraRowMapper<T> rowMapper) {
        return rs -> Flux.from(rs)
            .map(rowMapper::apply)
            .collectList();
    }
}
