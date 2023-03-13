package ru.tinkoff.kora.database.r2dbc;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.common.DataBaseModule;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcRowMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface R2dbcModule extends DataBaseModule {

    default <T> R2dbcResultFluxMapper<T, Mono<T>> monoR2dbcResultFluxMapper(R2dbcRowMapper<T> rowMapper) {
        return R2dbcResultFluxMapper.mono(rowMapper);
    }

    default <T> R2dbcResultFluxMapper<List<T>, Mono<List<T>>> monoListR2dbcResultFluxMapper(R2dbcRowMapper<T> rowMapper) {
        return R2dbcResultFluxMapper.monoList(rowMapper);
    }

    default <T> R2dbcResultFluxMapper<T, Flux<T>> fluxR2dbcResultFluxMapper(R2dbcRowMapper<T> rowMapper) {
        return R2dbcResultFluxMapper.flux(rowMapper);
    }

    default R2dbcRowMapper<String> stringR2dbcRowMapper() {
        return row -> row.get(0, String.class);
    }

    default R2dbcRowMapper<Integer> integerR2dbcRowMapper() {
        return row -> row.get(0, Integer.class);
    }

    default R2dbcRowMapper<Long> longR2dbcRowMapper() {
        return row -> row.get(0, Long.class);
    }

    default R2dbcRowMapper<Double> doubleR2dbcRowMapper() {
        return row -> row.get(0, Double.class);
    }

    default R2dbcRowMapper<LocalDate> localDateR2dbcRowMapper() {
        return row -> row.get(0, LocalDate.class);
    }

    default R2dbcRowMapper<LocalDateTime> localDateTimeR2dbcRowMapper() {
        return row -> row.get(0, LocalDateTime.class);
    }

    default R2dbcRowMapper<BigDecimal> bigDecimalR2dbcRowMapper() {
        return row -> row.get(0, BigDecimal.class);
    }

    default R2dbcRowMapper<BigInteger> bigIntegerR2dbcRowMapper() {
        return row -> row.get(0, BigInteger.class);
    }

    default R2dbcRowMapper<byte[]> byteArrayR2dbcRowMapper() {
        return row -> row.get(0, byte[].class);
    }
}
