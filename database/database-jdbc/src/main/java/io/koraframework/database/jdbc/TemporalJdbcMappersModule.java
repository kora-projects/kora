package io.koraframework.database.jdbc;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;

import java.sql.Types;
import java.time.*;

public interface TemporalJdbcMappersModule {

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

    @DefaultComponent
    default JdbcRowMapper<Instant> instantJdbcRowMapper() {
        return rs -> {
            var value = rs.getObject(1, OffsetDateTime.class);
            if (rs.wasNull()) {
                return null;
            }
            return value.toInstant();
        };
    }

    // Parameter Mappers
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

    @DefaultComponent
    default JdbcParameterColumnMapper<Instant> instantJdbcParameterColumnMapper() {
        return (stmt, index, o) -> {
            if (o == null) {
                stmt.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                stmt.setObject(index, o.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
            }
        };
    }

    // Result Mappers
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

    @DefaultComponent
    default JdbcResultColumnMapper<Instant> instantJdbcResultColumnMapper() {
        return (row, index) -> {
            var value = row.getObject(index, OffsetDateTime.class);
            if (row.wasNull()) {
                return null;
            }
            return value.toInstant();
        };
    }
}
