package ru.tinkoff.kora.database.cassandra;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;
import ru.tinkoff.kora.database.common.DataBaseModule;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CassandraModule extends DataBaseModule {

    default <T> CassandraResultSetMapper<T> cassandraSingleResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return CassandraResultSetMapper.singleResultSetMapper(rowMapper);
    }

    default <T> CassandraResultSetMapper<Optional<T>> cassandraOptionalResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return CassandraResultSetMapper.optionalResultSetMapper(rowMapper);
    }

    default <T> CassandraReactiveResultSetMapper<T, Flux<T>> cassandraFluxReactiveResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return CassandraReactiveResultSetMapper.flux(rowMapper);
    }

    default <T> CassandraReactiveResultSetMapper<T, Mono<T>> cassandraMonoReactiveResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return CassandraReactiveResultSetMapper.mono(rowMapper);
    }

    default <T> CassandraReactiveResultSetMapper<List<T>, Mono<List<T>>> cassandraMonoListReactiveResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return CassandraReactiveResultSetMapper.monoList(rowMapper);
    }

    default CassandraRowMapper<String> stringCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getString(0);
    }

    default CassandraRowMapper<Integer> integerCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getInt(1);
    }

    default CassandraRowMapper<Long> longCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getLong(1);
    }

    default CassandraRowMapper<Double> doubleCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getDouble(1);
    }

    default CassandraRowMapper<Boolean> booleanCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getBoolean(1);
    }

    default CassandraRowMapper<BigDecimal> bigDecimalCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getBigDecimal(1);
    }

    default CassandraRowMapper<ByteBuffer> byteBufferCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getByteBuffer(0);
    }

    default CassandraRowMapper<LocalDate> localDateCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getLocalDate(0);
    }

    default CassandraRowMapper<LocalDateTime> localDateTimeCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.get(0, LocalDateTime.class);
    }

    default CassandraReactiveResultSetMapper<Void, Mono<Void>> voidMonoCassandraReactiveResultSetMapper() {
        return rs -> Flux.from(rs).then();
    }
}
