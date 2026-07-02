package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.data.CqlDuration;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import io.koraframework.database.cassandra.mapper.result.CassandraResultSetMapper;
import io.koraframework.database.cassandra.mapper.result.CassandraRowColumnMapper;
import io.koraframework.database.cassandra.mapper.result.CassandraRowMapper;
import io.koraframework.database.common.DatabaseModule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public interface CassandraMapperModule extends DatabaseModule {

    @DefaultComponent
    default <T> CassandraResultSetMapper<Optional<T>> cassandraOptionalResultSetMapper(CassandraRowMapper<T> rowMapper) {
        return CassandraResultSetMapper.optionalResultSetMapper(rowMapper);
    }

    @DefaultComponent
    default CassandraRowMapper<String> stringCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getString(0);
    }

    @DefaultComponent
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

    @DefaultComponent
    default CassandraRowMapper<Integer> integerCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getInt(0);
    }

    @DefaultComponent
    default CassandraRowMapper<Long> longCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getLong(0);
    }

    @DefaultComponent
    default CassandraRowMapper<Double> doubleCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getDouble(0);
    }

    @DefaultComponent
    default CassandraRowMapper<Float> floatCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getFloat(0);
    }

    @DefaultComponent
    default CassandraRowMapper<Boolean> booleanCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getBoolean(0);
    }

    @DefaultComponent
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
    default CassandraRowMapper<byte[]> byteArrayCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : byteArray(row.getByteBuffer(0));
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

    @DefaultComponent
    default CassandraRowMapper<CqlDuration> cqlDurationCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getCqlDuration(0);
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<String> stringCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setString(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<Short> shortCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setShort(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<Byte> byteCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setByte(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<Integer> integerCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setInt(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<Long> longCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setLong(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<Double> doubleCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setDouble(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<Float> floatCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setFloat(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<Boolean> booleanCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setBoolean(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<BigDecimal> bigDecimalCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setBigDecimal(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<BigInteger> bigIntegerCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setBigInteger(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<UUID> uuidCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setUuid(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<ByteBuffer> byteBufferCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setByteBuffer(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<byte[]> byteArrayCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setByteBuffer(index, ByteBuffer.wrap(value));
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<LocalTime> localTimeCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setLocalTime(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<LocalDate> localDateCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setLocalDate(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<LocalDateTime> localDateTimeCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.set(index, value, LocalDateTime.class);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<ZonedDateTime> zonedDateTimeCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.set(index, value, ZonedDateTime.class);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<Instant> instantCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setInstant(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraParameterColumnMapper<CqlDuration> cqlDurationCassandraParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setToNull(index);
            } else {
                stmt.setCqlDuration(index, value);
            }
        };
    }

    @DefaultComponent
    default CassandraRowColumnMapper<String> stringCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getString(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<Short> shortCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getShort(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<Byte> byteCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getByte(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<Integer> integerCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getInt(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<Long> longCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getLong(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<Double> doubleCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getDouble(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<Float> floatCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getFloat(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<Boolean> booleanCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getBoolean(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<BigDecimal> bigDecimalCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getBigDecimal(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<BigInteger> bigIntegerCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getBigInteger(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<UUID> uuidCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getUuid(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<ByteBuffer> byteBufferCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getByteBuffer(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<byte[]> byteArrayCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : byteArray(row.getByteBuffer(index));
    }

    @DefaultComponent
    default CassandraRowColumnMapper<LocalTime> localTimeCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getLocalTime(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<LocalDate> localDateCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getLocalDate(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<LocalDateTime> localDateTimeCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.get(index, LocalDateTime.class);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<ZonedDateTime> zonedDateTimeCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.get(index, ZonedDateTime.class);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<Instant> instantCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getInstant(index);
    }

    @DefaultComponent
    default CassandraRowColumnMapper<CqlDuration> cqlDurationCassandraRowColumnMapper() {
        return (row, index) -> row.isNull(index)
            ? null
            : row.getCqlDuration(index);
    }

    private static byte[] byteArray(ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.position() == 0 && buffer.remaining() == buffer.array().length) {
            return Arrays.copyOf(buffer.array(), buffer.array().length);
        }
        var copy = new byte[buffer.remaining()];
        buffer.duplicate().get(copy);
        return copy;
    }
}
