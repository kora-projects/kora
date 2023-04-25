package ru.tinkoff.kora.database.r2dbc;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.database.common.DataBaseModule;
import ru.tinkoff.kora.database.r2dbc.mapper.parameter.R2dbcParameterColumnMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultColumnMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcRowMapper;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.List;
import java.util.UUID;

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

    default R2dbcRowMapper<BigDecimal> bigDecimalR2dbcRowMapper() {
        return row -> row.get(0, BigDecimal.class);
    }

    @DefaultComponent
    default R2dbcRowMapper<BigInteger> bigIntegerR2dbcRowMapper() {
        return row -> row.get(0, BigInteger.class);
    }

    default R2dbcRowMapper<byte[]> byteArrayR2dbcRowMapper() {
        return row -> row.get(0, byte[].class);
    }

    @DefaultComponent
    default R2dbcRowMapper<UUID> uuidR2dbcRowMapper() {
        return row -> row.get(0, UUID.class);
    }

    @DefaultComponent
    default R2dbcRowMapper<LocalDate> localDateR2dbcRowMapper() {
        return row -> row.get(0, LocalDate.class);
    }

    @DefaultComponent
    default R2dbcRowMapper<LocalTime> localTimeR2dbcRowMapper() {
        return row -> row.get(0, LocalTime.class);
    }

    @DefaultComponent
    default R2dbcRowMapper<LocalDateTime> localDateTimeR2dbcRowMapper() {
        return row -> row.get(0, LocalDateTime.class);
    }

    @DefaultComponent
    default R2dbcRowMapper<OffsetTime> offsetTimeR2dbcRowMapper() {
        return row -> row.get(0, OffsetTime.class);
    }

    @DefaultComponent
    default R2dbcRowMapper<OffsetDateTime> offsetDateTimeR2dbcRowMapper() {
        return row -> row.get(0, OffsetDateTime.class);
    }

    // Parameter Mapper
    @DefaultComponent
    default R2dbcParameterColumnMapper<BigDecimal> bigDecimalR2dbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.bindNull(index, BigDecimal.class);
            } else {
                stmt.bind(index, o);
            }
        };
    }

    @DefaultComponent
    default R2dbcParameterColumnMapper<UUID> uuidR2dbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.bindNull(index, UUID.class);
            } else {
                stmt.bind(index, o);
            }
        };
    }

    @DefaultComponent
    default R2dbcParameterColumnMapper<LocalDate> localDateR2dbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.bindNull(index, LocalDate.class);
            } else {
                stmt.bind(index, o);
            }
        };
    }

    @DefaultComponent
    default R2dbcParameterColumnMapper<LocalTime> LocalTimeR2dbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.bindNull(index, LocalTime.class);
            } else {
                stmt.bind(index, o);
            }
        };
    }

    @DefaultComponent
    default R2dbcParameterColumnMapper<LocalDateTime> LocalDateTimeR2dbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.bindNull(index, LocalDateTime.class);
            } else {
                stmt.bind(index, o);
            }
        };
    }

    @DefaultComponent
    default R2dbcParameterColumnMapper<OffsetTime> offsetTimeR2dbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.bindNull(index, OffsetTime.class);
            } else {
                stmt.bind(index, o);
            }
        };
    }

    @DefaultComponent
    default R2dbcParameterColumnMapper<OffsetDateTime> OffsetDateTimeR2dbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.bindNull(index, OffsetDateTime.class);
            } else {
                stmt.bind(index, o);
            }
        };
    }

    // Result Column Mappers
    @DefaultComponent
    default R2dbcResultColumnMapper<BigDecimal> bigDecimalR2dbcResultColumnMapper() {
        return (row, label) -> row.get(label, BigDecimal.class);
    }

    @DefaultComponent
    default R2dbcResultColumnMapper<UUID> uuidR2dbcResultColumnMapper() {
        return (row, label) -> row.get(label, UUID.class);
    }

    @DefaultComponent
    default R2dbcResultColumnMapper<LocalDate> localDateR2dbcResultColumnMapper() {
        return (row, label) -> row.get(label, LocalDate.class);
    }

    @DefaultComponent
    default R2dbcResultColumnMapper<LocalTime> localTimeR2dbcResultColumnMapper() {
        return (row, label) -> row.get(label, LocalTime.class);
    }

    @DefaultComponent
    default R2dbcResultColumnMapper<LocalDateTime> localDateTimeR2dbcResultColumnMapper() {
        return (row, label) -> row.get(label, LocalDateTime.class);
    }

    @DefaultComponent
    default R2dbcResultColumnMapper<OffsetTime> offsetTimeR2dbcResultColumnMapper() {
        return (row, label) -> row.get(label, OffsetTime.class);
    }

    @DefaultComponent
    default R2dbcResultColumnMapper<OffsetDateTime> offsetDateTimeR2dbcResultColumnMapper() {
        return (row, label) -> row.get(label, OffsetDateTime.class);
    }
}
