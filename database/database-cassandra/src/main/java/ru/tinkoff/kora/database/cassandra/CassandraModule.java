package ru.tinkoff.kora.database.cassandra;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;
import ru.tinkoff.kora.database.common.DataBaseModule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

public interface CassandraModule extends DataBaseModule {

    default <T> CassandraResultSetMapper<Optional<T>> cassandraOptionalResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return CassandraResultSetMapper.optionalResultSetMapper(rowMapper);
    }

    default CassandraRowMapper<String> stringCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getString(0);
    }

    default CassandraRowMapper<Short> shortCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getShort(0);
    }

    @DefaultComponent
    default CassandraRowMapper<Byte> byteCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getByte(0);
    }

    default CassandraRowMapper<Integer> integerCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getInt(0);
    }

    default CassandraRowMapper<Long> longCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getLong(0);
    }

    default CassandraRowMapper<Double> doubleCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getDouble(0);
    }

    default CassandraRowMapper<Float> floatCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getFloat(0);
    }

    default CassandraRowMapper<Boolean> booleanCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getBoolean(0);
    }

    default CassandraRowMapper<BigDecimal> bigDecimalCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getBigDecimal(0);
    }

    @DefaultComponent
    default CassandraRowMapper<BigInteger> bigIntegerCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getBigInteger(0);
    }

    @DefaultComponent
    default CassandraRowMapper<UUID> uuidCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getUuid(0);
    }

    @DefaultComponent
    default CassandraRowMapper<ByteBuffer> byteBufferCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getByteBuffer(0);
    }

    @DefaultComponent
    default CassandraRowMapper<LocalTime> localTimeCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getLocalTime(0);
    }

    @DefaultComponent
    default CassandraRowMapper<LocalDate> localDateCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getLocalDate(0);
    }

    @DefaultComponent
    default CassandraRowMapper<LocalDateTime> localDateTimeCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.get(0, LocalDateTime.class);
    }

    @DefaultComponent
    default CassandraRowMapper<ZonedDateTime> zonedDateTimeCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.get(0, ZonedDateTime.class);
    }

    @DefaultComponent
    default CassandraRowMapper<Instant> instantCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getInstant(0);
    }
}
