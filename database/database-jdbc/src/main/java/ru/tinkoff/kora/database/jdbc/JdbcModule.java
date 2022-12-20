package ru.tinkoff.kora.database.jdbc;

import ru.tinkoff.kora.database.common.DataBaseModule;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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

    default JdbcRowMapper<BigDecimal> bigDecimalJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, BigDecimal.class);
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

    default JdbcRowMapper<LocalDateTime> localDateTimeJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, LocalDateTime.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }

    default JdbcRowMapper<LocalDate> localDateJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, LocalDate.class);
            if (rs.wasNull()) {
                return null;
            }
            return value;
        };
    }
}
