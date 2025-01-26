package ru.tinkoff.kora.database.cassandra;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;
import ru.tinkoff.kora.database.common.*;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Types;
import java.time.*;
import java.util.HashMap;
import java.util.Optional;

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
            : row.getShort(1);
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

    default CassandraRowMapper<Float> floatCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getFloat(1);
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

    default CassandraRowMapper<LocalTime> localTimeCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getLocalTime(0);
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

    default CassandraRowMapper<ZonedDateTime> zonedDateTimeCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.get(0, ZonedDateTime.class);
    }

    default CassandraRowMapper<Instant> instantCassandraRowMapper() {
        return row -> row.isNull(0)
            ? null
            : row.getInstant(0);
    }

    // Parameter
    @DefaultComponent
    default <T extends Enum<T> & EnumColumnIntMapping> CassandraParameterColumnMapper<T> enumIntJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setToNull(index);
            } else {
                stmt.setInt(index, o.valueAsInt());
            }
        };
    }

    @DefaultComponent
    default <T extends Enum<T> & EnumColumnShortMapping> CassandraParameterColumnMapper<T> enumShortJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setToNull(index);
            } else {
                stmt.setShort(index, o.valueAsShort());
            }
        };
    }

    @DefaultComponent
    default <T extends Enum<T> & EnumColumnStringMapping> CassandraParameterColumnMapper<T> enumStringJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setToNull(index);
            } else {
                stmt.setString(index, o.toString());
            }
        };
    }

    // RowColumn
    @DefaultComponent
    default <T extends Enum<T> & EnumColumnIntMapping> CassandraRowColumnMapper<T> enumIntCassandraRowColumnMapper(TypeRef<T> typeRef) {
        final T[] enums = typeRef.getRawType().getEnumConstants();
        var enumMap = new HashMap<Integer, T>();
        for (T e : enums) {
            enumMap.put(e.valueAsInt(), e);
        }

        return (row, index) -> {
            if (row.isNull(index)) {
                return null;
            }

            var value = row.getInt(index);
            return enumMap.get(value);
        };
    }

    @DefaultComponent
    default <T extends Enum<T> & EnumColumnShortMapping> CassandraRowColumnMapper<T> enumShortCassandraRowColumnMapper(TypeRef<T> typeRef) {
        final T[] enums = typeRef.getRawType().getEnumConstants();
        var enumMap = new HashMap<Short, T>();
        for (T e : enums) {
            enumMap.put(e.valueAsShort(), e);
        }

        return (row, index) -> {
            if (row.isNull(index)) {
                return null;
            }

            var value = row.getShort(index);
            return enumMap.get(value);
        };
    }

    @DefaultComponent
    default <T extends Enum<T> & EnumColumnStringMapping> CassandraRowColumnMapper<T> enumStringCassandraRowColumnMapper(TypeRef<T> typeRef) {
        final T[] enums = typeRef.getRawType().getEnumConstants();
        var enumMap = new HashMap<String, T>();
        for (T e : enums) {
            enumMap.put(e.toString(), e);
        }

        return (row, index) -> {
            if (row.isNull(index)) {
                return null;
            }

            var value = row.getString(index);
            return enumMap.get(value);
        };
    }
}
