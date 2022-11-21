package ru.tinkoff.kora.database.r2dbc.mapper.result;

import io.r2dbc.spi.Result;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;

import java.util.List;

public interface R2dbcResultFluxMapper<T, P extends Publisher<T>> extends Mapping.MappingFunction {
    static <T> R2dbcResultFluxMapper<T, Mono<T>> mono(R2dbcRowMapper<T> rowMapper) {
        return resultFlux -> resultFlux.flatMap(result -> result.map((row, meta) -> rowMapper.apply(row))).last();
    }

    static <T> R2dbcResultFluxMapper<List<T>, Mono<List<T>>> monoList(R2dbcRowMapper<T> rowMapper) {
        return resultFlux -> resultFlux.flatMap(result -> result.map((row, meta) -> rowMapper.apply(row))).collectList();
    }
    static <T> R2dbcResultFluxMapper<T, Flux<T>> flux(R2dbcRowMapper<T> rowMapper) {
        return resultFlux -> resultFlux.flatMap(result -> result.map((row, meta) -> rowMapper.apply(row)));
    }

    P apply(Flux<Result> resultFlux);
}
