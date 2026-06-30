package io.koraframework.database.jdbc;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.common.DatabaseModule;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

public interface JdbcMapperModule extends DatabaseModule {

    @DefaultComponent
    default <T> JdbcResultSetMapper<Optional<T>> optionalResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return JdbcResultSetMapper.optionalResultSetMapper(rowMapper);
    }

    @DefaultComponent
    default JdbcRowMapper<Boolean> booleanJdbcRowMapper() {
        return rs -> {
            var value = rs.getBoolean(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<Short> shortJdbcRowMapper() {
        return rs -> {
            var value = rs.getShort(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<Byte> byteJdbcRowMapper() {
        return rs -> {
            var value = rs.getByte(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<Integer> integerJdbcRowMapper() {
        return rs -> {
            var value = rs.getInt(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<Long> longJdbcRowMapper() {
        return rs -> {
            var value = rs.getLong(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<Double> doubleJdbcRowMapper() {
        return rs -> {
            var value = rs.getDouble(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<Float> floatJdbcRowMapper() {
        return rs -> {
            var value = rs.getFloat(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<String> stringJdbcRowMapper() {
        return rs -> {
            var value = rs.getString(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<byte[]> byteArrayJdbcRowMapper() {
        return rs -> {
            var value = rs.getBytes(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<BigDecimal> bigDecimalJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, BigDecimal.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<UUID> uuidJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, UUID.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<LocalDate> localDateJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, LocalDate.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<LocalTime> localTimeJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, LocalTime.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<LocalDateTime> localDateTimeJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, LocalDateTime.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<OffsetTime> offsetTimeJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, OffsetTime.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcRowMapper<OffsetDateTime> offsetDateTimeJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, OffsetDateTime.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    // Parameter Mappers
    @DefaultComponent
    default JdbcParameterColumnMapper<Boolean> booleanJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.BOOLEAN);
            } else {
                stmt.setBoolean(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<Short> shortJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.SMALLINT);
            } else {
                stmt.setShort(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<Byte> byteJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.TINYINT);
            } else {
                stmt.setByte(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<Integer> integerJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.INTEGER);
            } else {
                stmt.setInt(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<Long> longJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.BIGINT);
            } else {
                stmt.setLong(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<Double> doubleJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.DOUBLE);
            } else {
                stmt.setDouble(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<Float> floatJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.FLOAT);
            } else {
                stmt.setFloat(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<String> stringJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.VARCHAR);
            } else {
                stmt.setString(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<byte[]> byteArrayJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.VARBINARY);
            } else {
                stmt.setBytes(index, o);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<BigDecimal> bigDecimalJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.NUMERIC);
            } else {
                stmt.setObject(index, o, Types.NUMERIC);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<UUID> uuidJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.OTHER);
            } else {
                stmt.setObject(index, o, Types.OTHER);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<LocalDate> localDateJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.DATE);
            } else {
                stmt.setObject(index, o, Types.DATE);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<LocalTime> localTimeJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.TIME);
            } else {
                stmt.setObject(index, o, Types.TIME);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<LocalDateTime> LocalDateTimeJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.TIMESTAMP);
            } else {
                stmt.setObject(index, o, Types.TIMESTAMP);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<OffsetTime> offsetTimeJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.TIME_WITH_TIMEZONE);
            } else {
                stmt.setObject(index, o, Types.TIME_WITH_TIMEZONE);
            }
        };
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<OffsetDateTime> offsetDateTimeJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                stmt.setObject(index, o, Types.TIMESTAMP_WITH_TIMEZONE);
            }
        };
    }

    // Result Mappers
    @DefaultComponent
    default JdbcResultColumnMapper<Boolean> booleanJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getBoolean(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<Short> shortJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getShort(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<Byte> byteJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getByte(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<Integer> integerJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getInt(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<Long> longJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getLong(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<Double> doubleJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getDouble(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<Float> floatJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getFloat(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<String> stringJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getString(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<byte[]> byteArrayJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getBytes(index);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<BigDecimal> bigDecimalJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, BigDecimal.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<UUID> uuidJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, UUID.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<LocalDate> localDateJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, LocalDate.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<LocalTime> localTimeJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, LocalTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<LocalDateTime> localDateTimeJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, LocalDateTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<OffsetTime> offsetTimeJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, OffsetTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<OffsetDateTime> offsetDateTimeJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, OffsetDateTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }
}
