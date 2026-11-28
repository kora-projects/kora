package io.koraframework.database.jdbc;

import io.koraframework.common.annotation.*;
import io.koraframework.database.common.*;
import io.koraframework.database.jdbc.mapper.parameter.*;
import io.koraframework.database.jdbc.mapper.result.*;
import org.jspecify.annotations.*;

import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public interface JdbcMapperModule extends DatabaseModule {

    @DefaultComponent
    default <T> JdbcResultSetMapper<Optional<T>> optionalResultSetMapper(JdbcRowMapper<T> rowMapper) {
        return JdbcResultSetMapper.optionalResultSetMapper(rowMapper);
    }

    @DefaultComponent
    default JdbcRowMapper<Boolean> booleanJdbcRowMapper() {return Mappers.BOOLEAN;}

    @DefaultComponent
    default JdbcRowMapper<Short> shortJdbcRowMapper() {return Mappers.SHORT;}

    @DefaultComponent
    default JdbcRowMapper<Byte> byteJdbcRowMapper() {return Mappers.BYTE;}

    @DefaultComponent
    default JdbcRowMapper<Integer> integerJdbcRowMapper() {return Mappers.INTEGER;}

    @DefaultComponent
    default JdbcRowMapper<Long> longJdbcRowMapper() {return Mappers.LONG;}

    @DefaultComponent
    default JdbcRowMapper<Double> doubleJdbcRowMapper() {return Mappers.DOUBLE;}

    @DefaultComponent
    default JdbcRowMapper<Float> floatJdbcRowMapper() {return Mappers.FLOAT;}

    @DefaultComponent
    default JdbcRowMapper<String> stringJdbcRowMapper() {return Mappers.STRING;}

    @DefaultComponent
    default JdbcRowMapper<byte[]> byteArrayJdbcRowMapper() {return Mappers.BYTE_ARRAY;}

    @DefaultComponent
    default JdbcRowMapper<BigDecimal> bigDecimalJdbcRowMapper() {return Mappers.BIG_DECIMAL;}

    @DefaultComponent
    default JdbcRowMapper<UUID> uuidJdbcRowMapper() {return Mappers.UUID;}

    @DefaultComponent
    default JdbcRowMapper<LocalDate> localDateJdbcRowMapper() {return Mappers.LOCAL_DATE;}

    @DefaultComponent
    default JdbcRowMapper<LocalTime> localTimeJdbcRowMapper() {return Mappers.LOCAL_TIME;}

    @DefaultComponent
    default JdbcRowMapper<LocalDateTime> localDateTimeJdbcRowMapper() {return Mappers.LOCAL_DATE_TIME;}

    @DefaultComponent
    default JdbcRowMapper<OffsetTime> offsetTimeJdbcRowMapper() {return Mappers.OFFSET_TIME;}

    @DefaultComponent
    default JdbcRowMapper<OffsetDateTime> offsetDateTimeJdbcRowMapper() {return Mappers.OFFSET_DATE_TIME;}

    // Parameter Mappers
    @DefaultComponent
    default JdbcParameterColumnMapper<Boolean> booleanJdbcParameterColumnMapper() {return ParameterMappers.BOOLEAN;}

    @DefaultComponent
    default JdbcParameterColumnMapper<Short> shortJdbcParameterColumnMapper() {return ParameterMappers.SHORT;}

    @DefaultComponent
    default JdbcParameterColumnMapper<Byte> byteJdbcParameterColumnMapper() {return ParameterMappers.BYTE;}

    @DefaultComponent
    default JdbcParameterColumnMapper<Integer> integerJdbcParameterColumnMapper() {return ParameterMappers.INTEGER;}

    @DefaultComponent
    default JdbcParameterColumnMapper<Long> longJdbcParameterColumnMapper() {return ParameterMappers.LONG;}

    @DefaultComponent
    default JdbcParameterColumnMapper<Double> doubleJdbcParameterColumnMapper() {return ParameterMappers.DOUBLE;}

    @DefaultComponent
    default JdbcParameterColumnMapper<Float> floatJdbcParameterColumnMapper() {return ParameterMappers.FLOAT;}

    @DefaultComponent
    default JdbcParameterColumnMapper<String> stringJdbcParameterColumnMapper() {return ParameterMappers.STRING;}

    @DefaultComponent
    default JdbcParameterColumnMapper<byte[]> byteArrayJdbcParameterColumnMapper() {return ParameterMappers.BYTE_ARRAY;}

    @DefaultComponent
    default JdbcParameterColumnMapper<BigDecimal> bigDecimalJdbcParameterColumnMapper() {return ParameterMappers.BIG_DECIMAL;}

    @DefaultComponent
    default JdbcParameterColumnMapper<UUID> uuidJdbcParameterColumnMapper() {return ParameterMappers.UUID;}

    @DefaultComponent
    default JdbcParameterColumnMapper<LocalDate> localDateJdbcParameterColumnMapper() {return ParameterMappers.LOCAL_DATE;}

    @DefaultComponent
    default JdbcParameterColumnMapper<LocalTime> localTimeJdbcParameterColumnMapper() {return ParameterMappers.LOCAL_TIME;}

    @DefaultComponent
    default JdbcParameterColumnMapper<LocalDateTime> LocalDateTimeJdbcParameterColumnMapper() {return ParameterMappers.LOCAL_DATE_TIME;}

    @DefaultComponent
    default JdbcParameterColumnMapper<OffsetTime> offsetTimeJdbcParameterColumnMapper() {return ParameterMappers.OFFSET_TIME;}

    @DefaultComponent
    default JdbcParameterColumnMapper<OffsetDateTime> offsetDateTimeJdbcParameterColumnMapper() {return ParameterMappers.OFFSET_DATE_TIME;}

    // Result Mappers
    @DefaultComponent
    default JdbcResultColumnMapper<Boolean> booleanJdbcResultColumnMapper() {return ColumnMappers.BOOLEAN;}

    @DefaultComponent
    default JdbcResultColumnMapper<Short> shortJdbcResultColumnMapper() {return ColumnMappers.SHORT;}

    @DefaultComponent
    default JdbcResultColumnMapper<Byte> byteJdbcResultColumnMapper() {return ColumnMappers.BYTE;}

    @DefaultComponent
    default JdbcResultColumnMapper<Integer> integerJdbcResultColumnMapper() {return ColumnMappers.INTEGER;}

    @DefaultComponent
    default JdbcResultColumnMapper<Long> longJdbcResultColumnMapper() {return ColumnMappers.LONG;}

    @DefaultComponent
    default JdbcResultColumnMapper<Double> doubleJdbcResultColumnMapper() {return ColumnMappers.DOUBLE;}

    @DefaultComponent
    default JdbcResultColumnMapper<Float> floatJdbcResultColumnMapper() {return ColumnMappers.FLOAT;}

    @DefaultComponent
    default JdbcResultColumnMapper<String> stringJdbcResultColumnMapper() {return ColumnMappers.STRING;}

    @DefaultComponent
    default JdbcResultColumnMapper<byte[]> byteArrayJdbcResultColumnMapper() {return ColumnMappers.BYTE_ARRAY;}

    @DefaultComponent
    default JdbcResultColumnMapper<BigDecimal> bigDecimalJdbcResultColumnMapper() {return ColumnMappers.BIG_DECIMAL;}

    @DefaultComponent
    default JdbcResultColumnMapper<UUID> uuidJdbcResultColumnMapper() {return ColumnMappers.UUID;}

    @DefaultComponent
    default JdbcResultColumnMapper<LocalDate> localDateJdbcResultColumnMapper() {return ColumnMappers.LOCAL_DATE;}

    @DefaultComponent
    default JdbcResultColumnMapper<LocalTime> localTimeJdbcResultColumnMapper() {return ParameterMappers.LOCAL_TIME_RESULT;}

    @DefaultComponent
    default JdbcResultColumnMapper<LocalDateTime> localDateTimeJdbcResultColumnMapper() {return ColumnMappers.LOCAL_DATE_TIME;}

    @DefaultComponent
    default JdbcResultColumnMapper<OffsetTime> offsetTimeJdbcResultColumnMapper() {return ColumnMappers.OFFSET_TIME;}

    @DefaultComponent
    default JdbcResultColumnMapper<OffsetDateTime> offsetDateTimeJdbcResultColumnMapper() {return ColumnMappers.OFFSET_DATE_TIME;}

    class Mappers {
        public static final BooleanRowMapper BOOLEAN = new BooleanRowMapper();
        public static final ShortRowMapper SHORT = new ShortRowMapper();
        public static final ByteRowMapper BYTE = new ByteRowMapper();
        public static final IntegerRowMapper INTEGER = new IntegerRowMapper();
        public static final LongRowMapper LONG = new LongRowMapper();
        public static final DoubleRowMapper DOUBLE = new DoubleRowMapper();
        public static final FloatRowMapper FLOAT = new FloatRowMapper();
        public static final StringRowMapper STRING = new StringRowMapper();
        public static final ByteArrayRowMapper BYTE_ARRAY = new ByteArrayRowMapper();
        public static final BigDecimalRowMapper BIG_DECIMAL = new BigDecimalRowMapper();
        public static final UuidRowMapper UUID = new UuidRowMapper();
        public static final LocalDateRowMapper LOCAL_DATE = new LocalDateRowMapper();
        public static final LocalTimeRowMapper LOCAL_TIME = new LocalTimeRowMapper();
        public static final LocalDateTimeRowMapper LOCAL_DATE_TIME = new LocalDateTimeRowMapper();
        public static final OffsetTimeRowMapper OFFSET_TIME = new OffsetTimeRowMapper();
        public static final OffsetDateTimeRowMapper OFFSET_DATE_TIME = new OffsetDateTimeRowMapper();

        public static final class BooleanRowMapper implements JdbcRowMapper<Boolean> {
            @Override
            public @Nullable Boolean apply(ResultSet rs) throws SQLException {
                var value = rs.getBoolean(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class ShortRowMapper implements JdbcRowMapper<Short> {
            @Override
            public @Nullable Short apply(ResultSet rs) throws SQLException {
                var value = rs.getShort(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class ByteRowMapper implements JdbcRowMapper<Byte> {
            @Override
            public @Nullable Byte apply(ResultSet rs) throws SQLException {
                var value = rs.getByte(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class IntegerRowMapper implements JdbcRowMapper<Integer> {
            @Override
            public @Nullable Integer apply(ResultSet rs) throws SQLException {
                var value = rs.getInt(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class LongRowMapper implements JdbcRowMapper<Long> {
            @Override
            public @Nullable Long apply(ResultSet rs) throws SQLException {
                var value = rs.getLong(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class DoubleRowMapper implements JdbcRowMapper<Double> {
            @Override
            public @Nullable Double apply(ResultSet rs) throws SQLException {
                var value = rs.getDouble(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class FloatRowMapper implements JdbcRowMapper<Float> {
            @Override
            public @Nullable Float apply(ResultSet rs) throws SQLException {
                var value = rs.getFloat(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class StringRowMapper implements JdbcRowMapper<String> {
            @Override
            public @Nullable String apply(ResultSet rs) throws SQLException {
                var value = rs.getString(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class ByteArrayRowMapper implements JdbcRowMapper<byte[]> {
            @Override
            public byte @Nullable [] apply(ResultSet rs) throws SQLException {
                var value = rs.getBytes(1);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class BigDecimalRowMapper implements JdbcRowMapper<BigDecimal> {
            @Override
            public @Nullable BigDecimal apply(ResultSet rs) throws SQLException {
                var value = rs.getObject(1, BigDecimal.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class UuidRowMapper implements JdbcRowMapper<UUID> {
            @Override
            public @Nullable UUID apply(ResultSet rs) throws SQLException {
                var value = rs.getObject(1, UUID.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class LocalDateRowMapper implements JdbcRowMapper<LocalDate> {
            @Override
            public @Nullable LocalDate apply(ResultSet rs) throws SQLException {
                var value = rs.getObject(1, LocalDate.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class LocalTimeRowMapper implements JdbcRowMapper<LocalTime> {
            @Override
            public @Nullable LocalTime apply(ResultSet rs) throws SQLException {
                var value = rs.getObject(1, LocalTime.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class LocalDateTimeRowMapper implements JdbcRowMapper<LocalDateTime> {
            @Override
            public @Nullable LocalDateTime apply(ResultSet rs) throws SQLException {
                var value = rs.getObject(1, LocalDateTime.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class OffsetTimeRowMapper implements JdbcRowMapper<OffsetTime> {
            @Override
            public @Nullable OffsetTime apply(ResultSet rs) throws SQLException {
                var value = rs.getObject(1, OffsetTime.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class OffsetDateTimeRowMapper implements JdbcRowMapper<OffsetDateTime> {
            @Override
            public @Nullable OffsetDateTime apply(ResultSet rs) throws SQLException {
                var value = rs.getObject(1, OffsetDateTime.class);
                return rs.wasNull() ? null : value;
            }
        }
    }

    class ParameterMappers {
        public static final BooleanParamMapper BOOLEAN = new BooleanParamMapper();
        public static final ShortParamMapper SHORT = new ShortParamMapper();
        public static final ByteParamMapper BYTE = new ByteParamMapper();
        public static final IntegerParamMapper INTEGER = new IntegerParamMapper();
        public static final LongParamMapper LONG = new LongParamMapper();
        public static final DoubleParamMapper DOUBLE = new DoubleParamMapper();
        public static final FloatParamMapper FLOAT = new FloatParamMapper();
        public static final StringParamMapper STRING = new StringParamMapper();
        public static final ByteArrayParamMapper BYTE_ARRAY = new ByteArrayParamMapper();
        public static final BigDecimalParamMapper BIG_DECIMAL = new BigDecimalParamMapper();
        public static final UuidParamMapper UUID = new UuidParamMapper();
        public static final LocalDateParamMapper LOCAL_DATE = new LocalDateParamMapper();
        public static final LocalTimeParamMapper LOCAL_TIME = new LocalTimeParamMapper();
        public static final LocalDateTimeParamMapper LOCAL_DATE_TIME = new LocalDateTimeParamMapper();
        public static final OffsetTimeParamMapper OFFSET_TIME = new OffsetTimeParamMapper();
        public static final OffsetDateTimeParamMapper OFFSET_DATE_TIME = new OffsetDateTimeParamMapper();
        public static final ColumnMappers.LocalTimeColMapper LOCAL_TIME_RESULT = new ColumnMappers.LocalTimeColMapper();

        public static final class BooleanParamMapper implements JdbcParameterColumnMapper<Boolean> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Boolean o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.BOOLEAN);} else stmt.setBoolean(index, o);
            }
        }

        public static final class ShortParamMapper implements JdbcParameterColumnMapper<Short> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Short o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.SMALLINT);} else stmt.setShort(index, o);
            }
        }

        public static final class ByteParamMapper implements JdbcParameterColumnMapper<Byte> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Byte o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.TINYINT);} else stmt.setByte(index, o);
            }
        }

        public static final class IntegerParamMapper implements JdbcParameterColumnMapper<Integer> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Integer o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.INTEGER);} else stmt.setInt(index, o);
            }
        }

        public static final class LongParamMapper implements JdbcParameterColumnMapper<Long> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Long o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.BIGINT);} else stmt.setLong(index, o);
            }
        }

        public static final class DoubleParamMapper implements JdbcParameterColumnMapper<Double> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Double o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.DOUBLE);} else stmt.setDouble(index, o);
            }
        }

        public static final class FloatParamMapper implements JdbcParameterColumnMapper<Float> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Float o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.FLOAT);} else stmt.setFloat(index, o);
            }
        }

        public static final class StringParamMapper implements JdbcParameterColumnMapper<String> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable String o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.VARCHAR);} else stmt.setString(index, o);
            }
        }

        public static final class ByteArrayParamMapper implements JdbcParameterColumnMapper<byte[]> {
            @Override
            public void set(PreparedStatement stmt, int index, byte @Nullable [] o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.VARBINARY);} else stmt.setBytes(index, o);
            }
        }

        public static final class BigDecimalParamMapper implements JdbcParameterColumnMapper<BigDecimal> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable BigDecimal o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.NUMERIC);} else stmt.setObject(index, o, Types.NUMERIC);
            }
        }

        public static final class UuidParamMapper implements JdbcParameterColumnMapper<UUID> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable UUID o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.OTHER);} else stmt.setObject(index, o, Types.OTHER);
            }
        }

        public static final class LocalDateParamMapper implements JdbcParameterColumnMapper<LocalDate> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable LocalDate o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.DATE);} else stmt.setObject(index, o, Types.DATE);
            }
        }

        public static final class LocalTimeParamMapper implements JdbcParameterColumnMapper<LocalTime> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable LocalTime o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.TIME);} else stmt.setObject(index, o, Types.TIME);
            }
        }

        public static final class LocalDateTimeParamMapper implements JdbcParameterColumnMapper<LocalDateTime> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable LocalDateTime o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.TIMESTAMP);} else stmt.setObject(index, o, Types.TIMESTAMP);
            }
        }

        public static final class OffsetTimeParamMapper implements JdbcParameterColumnMapper<OffsetTime> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable OffsetTime o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.TIME_WITH_TIMEZONE);} else stmt.setObject(index, o, Types.TIME_WITH_TIMEZONE);
            }
        }

        public static final class OffsetDateTimeParamMapper implements JdbcParameterColumnMapper<OffsetDateTime> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable OffsetDateTime o) throws SQLException {
                if (o == null) {stmt.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);} else stmt.setObject(index, o, Types.TIMESTAMP_WITH_TIMEZONE);
            }
        }
    }

    class ColumnMappers {
        public static final BooleanColMapper BOOLEAN = new BooleanColMapper();
        public static final ShortColMapper SHORT = new ShortColMapper();
        public static final ByteColMapper BYTE = new ByteColMapper();
        public static final IntegerColMapper INTEGER = new IntegerColMapper();
        public static final LongColMapper LONG = new LongColMapper();
        public static final DoubleColMapper DOUBLE = new DoubleColMapper();
        public static final FloatColMapper FLOAT = new FloatColMapper();
        public static final StringColMapper STRING = new StringColMapper();
        public static final ByteArrayColMapper BYTE_ARRAY = new ByteArrayColMapper();
        public static final BigDecimalColMapper BIG_DECIMAL = new BigDecimalColMapper();
        public static final UuidColMapper UUID = new UuidColMapper();
        public static final LocalDateColMapper LOCAL_DATE = new LocalDateColMapper();
        public static final LocalDateTimeColMapper LOCAL_DATE_TIME = new LocalDateTimeColMapper();
        public static final OffsetTimeColMapper OFFSET_TIME = new OffsetTimeColMapper();
        public static final OffsetDateTimeColMapper OFFSET_DATE_TIME = new OffsetDateTimeColMapper();

        public static final class BooleanColMapper implements JdbcResultColumnMapper<Boolean> {
            @Override
            public @Nullable Boolean apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getBoolean(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class ShortColMapper implements JdbcResultColumnMapper<Short> {
            @Override
            public @Nullable Short apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getShort(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class ByteColMapper implements JdbcResultColumnMapper<Byte> {
            @Override
            public @Nullable Byte apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getByte(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class IntegerColMapper implements JdbcResultColumnMapper<Integer> {
            @Override
            public @Nullable Integer apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getInt(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class LongColMapper implements JdbcResultColumnMapper<Long> {
            @Override
            public @Nullable Long apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getLong(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class DoubleColMapper implements JdbcResultColumnMapper<Double> {
            @Override
            public @Nullable Double apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getDouble(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class FloatColMapper implements JdbcResultColumnMapper<Float> {
            @Override
            public @Nullable Float apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getFloat(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class StringColMapper implements JdbcResultColumnMapper<String> {
            @Override
            public @Nullable String apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getString(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class ByteArrayColMapper implements JdbcResultColumnMapper<byte[]> {
            @Override
            public byte @Nullable [] apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getBytes(index);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class BigDecimalColMapper implements JdbcResultColumnMapper<BigDecimal> {
            @Override
            public @Nullable BigDecimal apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getObject(index, BigDecimal.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class UuidColMapper implements JdbcResultColumnMapper<UUID> {
            @Override
            public @Nullable UUID apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getObject(index, UUID.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class LocalDateColMapper implements JdbcResultColumnMapper<LocalDate> {
            @Override
            public @Nullable LocalDate apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getObject(index, LocalDate.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class LocalTimeColMapper implements JdbcResultColumnMapper<LocalTime> {
            @Override
            public @Nullable LocalTime apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getObject(index, LocalTime.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class LocalDateTimeColMapper implements JdbcResultColumnMapper<LocalDateTime> {
            @Override
            public @Nullable LocalDateTime apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getObject(index, LocalDateTime.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class OffsetTimeColMapper implements JdbcResultColumnMapper<OffsetTime> {
            @Override
            public @Nullable OffsetTime apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getObject(index, OffsetTime.class);
                return rs.wasNull() ? null : value;
            }
        }

        public static final class OffsetDateTimeColMapper implements JdbcResultColumnMapper<OffsetDateTime> {
            @Override
            public @Nullable OffsetDateTime apply(ResultSet rs, int index) throws SQLException {
                var value = rs.getObject(index, OffsetDateTime.class);
                return rs.wasNull() ? null : value;
            }
        }
    }
}
