package ru.tinkoff.kora.database.r2dbc.mapper.result;

import io.r2dbc.spi.Result;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.r2dbc.R2dbcRepository;

import java.util.List;
import java.util.Optional;

/**
 * <b>Русский</b>: Контракт для создания конвертера <b>результата</b> SQL запроса.
 * <br>
 * Предоставляется над возвращаемым значением метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a SQL query <b>result</b> converter.
 * <br>
 * Provided over the return value of the method via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends R2dbcRepository {
 *
 *     @Mapping(MyR2dbcResultFluxMapper.class)
 *     @Query("SELECT u.name, u.surname FROM users u")
 *     List<User> findAll();
 * }
 * }
 * </pre>
 *
 * @see R2dbcRepository
 * @see Mapping
 */
public interface R2dbcResultFluxMapper<T, P extends Publisher<T>> extends Mapping.MappingFunction {
    static <T> R2dbcResultFluxMapper<T, Mono<T>> mono(R2dbcRowMapper<T> rowMapper) {
        return resultFlux -> resultFlux.flatMap(result -> result.map((row, meta) -> rowMapper.apply(row))).takeLast(1).next();
    }

    static <T> R2dbcResultFluxMapper<List<T>, Mono<List<T>>> monoList(R2dbcRowMapper<T> rowMapper) {
        return resultFlux -> resultFlux.flatMap(result -> result.map((row, meta) -> rowMapper.apply(row))).collectList();
    }

    static <T> R2dbcResultFluxMapper<Optional<T>, Mono<Optional<T>>> monoOptional(R2dbcRowMapper<T> rowMapper) {
        return resultFlux -> resultFlux.flatMap(result -> result.map((row, meta) -> rowMapper.apply(row))).takeLast(1).next().map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    static <T> R2dbcResultFluxMapper<T, Flux<T>> flux(R2dbcRowMapper<T> rowMapper) {
        return resultFlux -> resultFlux.flatMap(result -> result.map((row, meta) -> rowMapper.apply(row)));
    }

    P apply(Flux<Result> resultFlux);
}
