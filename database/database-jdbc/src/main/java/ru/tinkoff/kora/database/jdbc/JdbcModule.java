package ru.tinkoff.kora.database.jdbc;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.database.common.DataBaseModule;
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

public interface JdbcModule extends DataBaseModule {

    default <T> JdbcResultSetMapper<Optional<T>> optionalResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return JdbcResultSetMapper.optionalResultSetMapper(rowMapper);
    }

    default JdbcRowMapper<Boolean> booleanJdbcRowMapper() {
        return rs -> {
            var value = rs.getBoolean(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    default JdbcRowMapper<Integer> integerJdbcRowMapper() {
        return rs -> {
            var value = rs.getInt(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    default JdbcRowMapper<Long> longJdbcRowMapper() {
        return rs -> {
            var value = rs.getLong(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    default JdbcRowMapper<Double> doubleJdbcRowMapper() {
        return rs -> {
            var value = rs.getDouble(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    default JdbcRowMapper<String> stringJdbcRowMapper() {
        return rs -> {
            var value = rs.getString(1);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

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
    default JdbcResultColumnMapper<LocalDate> localDateJdbcColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, LocalDate.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<LocalTime> localTimeJdbcColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, LocalTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<LocalDateTime> localDateTimeJdbcColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, LocalDateTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<OffsetTime> offsetTimeJdbcColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, OffsetTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }

    @DefaultComponent
    default JdbcResultColumnMapper<OffsetDateTime> offsetDateTimeJdbcColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, OffsetDateTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value;
        };
    }
}
