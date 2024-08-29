package ru.tinkoff.kora.database.cassandra.mapper.result;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;

import java.util.List;

/**
 * <b>Русский</b>: Контракт для создания конвертера <b>результата</b> CQL запроса в реактивной парадигме.
 * <br>
 * Предоставляется над возвращаемым значением метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a CQL query <b>result</b> converter in a reactive paradigm.
 * <br>
 * Provided over the return value of the method via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends CassandraRepository {
 *
 *     @Mapping(MyCassandraReactiveResultSetMapper.class)
 *     @Query("SELECT u.name, u.surname FROM users u")
 *     Flux<User> findAll();
 * }
 * }
 * </pre>
 *
 * @see CassandraRepository
 * @see Mapping
 */
public interface CassandraReactiveResultSetMapper<T, P extends Publisher<T>> extends Mapping.MappingFunction {
    P apply(ReactiveResultSet rows);

    static <T> CassandraReactiveResultSetMapper<T, Flux<T>> flux(CassandraRowMapper<T> rowMapper) {
        return rs -> Flux.from(rs).mapNotNull(rowMapper::apply);
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

    static CassandraReactiveResultSetMapper<Void, Mono<Void>> monoVoid() {
        return rs -> Flux.from(rs).then();
    }

    static <T> CassandraReactiveResultSetMapper<List<T>, Mono<List<T>>> monoList(CassandraRowMapper<T> rowMapper) {
        return rs -> Flux.from(rs)
            .mapNotNull(rowMapper::apply)
            .collectList();
    }
}
